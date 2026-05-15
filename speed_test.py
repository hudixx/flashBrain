import os
import time
from paddleocr import PaddleOCR
import cv2

os.environ['FLAGS_use_mkldnn'] = '0'

def test_speed(model_type, use_mobile=True):
    print(f"\n--- Testing {model_type} (use_mobile={use_mobile}) ---")
    start_init = time.time()
    ocr = PaddleOCR(lang="ch", device="cpu", use_mobile=use_mobile, enable_mkldnn=False)
    print(f"Init time: {time.time() - start_init:.2f}s")
    
    img = cv2.imread("docs/111111111.png")
    padding = 50
    img = cv2.copyMakeBorder(img, padding, padding, padding, padding, cv2.BORDER_CONSTANT, value=[255, 255, 255])
    
    # Warmup
    ocr.ocr(img)
    
    start_proc = time.time()
    for _ in range(5):
        ocr.ocr(img)
    avg_time = (time.time() - start_proc) / 5
    print(f"Average inference time: {avg_time:.2f}s")

if __name__ == "__main__":
    # Current setup (Server models are being used as seen in logs)
    test_speed("Server (Current)", use_mobile=False)
    # Mobile setup
    test_speed("Mobile", use_mobile=True)
