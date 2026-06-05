import os
import psutil
import time
import sys
import multiprocessing
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from paddleocr import PaddleOCR
import numpy as np
import cv2
import io

# 禁用 oneDNN 和 PIR API 以避免某些平台上的兼容性问题
# 注意：在 3.2.2 版本下，我们恢复 mkldnn 加速以提升性能
os.environ['FLAGS_use_mkldnn'] = '1'
os.environ['FLAGS_enable_pir_api'] = '0'
os.environ['FLAGS_enable_pir_in_executor'] = '0'

app = FastAPI(title="FlashBrain OCR Server")

# 启用 CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 全局初始化 OCR 模型 (使用简体中文)
# 第一次启动时会自动 download 模型
try:
    # 优化点：增加 cpu_threads 充分利用多核(当前机器有20核)
    # 优化点：use_angle_cls=False 禁用文字方向分类器，可加快识别(假设图片为正向)
    cpu_threads = 20
    ocr = PaddleOCR(
        lang="ch", 
        device="cpu", 
        enable_mkldnn=True,
        cpu_threads=cpu_threads,
        use_angle_cls=False
    )
except Exception as e:
    print(f"Error initializing PaddleOCR: {e}")
    ocr = None

@app.get("/")
async def root():
    return {"message": "FlashBrain OCR Server is running", "ocr_enabled": ocr is not None}

@app.post("/ocr")
async def perform_ocr(file: UploadFile = File(...)):
    if ocr is None:
        raise HTTPException(status_code=500, detail="OCR engine not initialized")
    
    try:
        # 读取图片内容
        start_time = time.time()
        contents = await file.read()
        nparr = np.frombuffer(contents, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if img is None:
            raise HTTPException(status_code=400, detail="Invalid image file")

        # 优化点：限制图像最大尺寸，过大的高清图片(如4K截图)会导致 CPU 推理极慢
        max_side = 1280
        h, w = img.shape[:2]
        if max(h, w) > max_side:
            scale = max_side / max(h, w)
            img = cv2.resize(img, (int(w * scale), int(h * scale)))

        # 增加内边距以提高识别准确率 (处理边缘文字识别不全的问题)
        padding = 50
        img = cv2.copyMakeBorder(img, padding, padding, padding, padding, cv2.BORDER_CONSTANT, value=[255, 255, 255])

        # 执行识别
        # 在 3.x 中 ocr.ocr() 返回列表嵌套字典格式，与 2.x 的列表嵌套列表格式不同
        result = ocr.ocr(img)
        
        # 提取并合并文字
        text_lines = []
        if result and isinstance(result, list):
            # 处理 PaddleOCR 3.x (PaddleX) 格式
            if len(result) > 0 and isinstance(result[0], dict) and 'rec_texts' in result[0]:
                text_lines = result[0]['rec_texts']
            # 处理 PaddleOCR 2.x 格式 (后向兼容)
            elif len(result) > 0 and isinstance(result[0], list):
                for line in result[0]:
                    if isinstance(line, list) and len(line) > 1 and isinstance(line[1], tuple):
                        text_lines.append(line[1][0])
        
        full_text = "\n".join(text_lines)
        processing_time = time.time() - start_time
        
        return {
            "filename": file.filename,
            "text": full_text,
            "status": "success",
            "processing_time_sec": round(processing_time, 2)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"OCR processing failed: {str(e)}")

def manage_port_conflict(port):
    """
    检查端口是否被占用，如果是旧s的 ocr-server 进程则尝试终止，否则报错退出。
    """
    for conn in psutil.net_connections(kind='inet'):
        if conn.laddr.port == port and conn.status == 'LISTEN':
            pid = conn.pid
            if pid is None or pid == os.getpid():
                continue
            
            try:
                proc = psutil.Process(pid)
                cmdline = proc.cmdline()
                
                # 检查是否是旧的 ocr-server (通过 main.py 判断)
                if any("main.py" in arg for arg in cmdline):
                    print(f"检测到旧版 OCR Server (PID: {pid}) 正在占用端口 {port}，正在尝试重启...")
                    proc.terminate()
                    
                    # 等待端口释放 (最多等待 5 秒)
                    for _ in range(50):
                        time.sleep(0.1)
                        if not any(c.laddr.port == port and c.status == 'LISTEN' for c in psutil.net_connections(kind='inet')):
                            return
                    
                    # 如果仍占用，强制杀死
                    if proc.is_running():
                        proc.kill()
                        proc.wait(1)
                else:
                    print(f"错误: 端口 {port} 已被非 OCR Server 进程占用 (PID: {pid}, 命令: {' '.join(cmdline)})")
                    sys.exit(1)
                    
            except (psutil.NoSuchProcess, psutil.AccessDenied) as e:
                print(f"处理端口冲突时出错: {e}")
                sys.exit(1)

if __name__ == "__main__":
    import uvicorn
    # 统一使用 8093 端口
    port = 8093
    manage_port_conflict(port)
    uvicorn.run(app, host="0.0.0.0", port=port)
