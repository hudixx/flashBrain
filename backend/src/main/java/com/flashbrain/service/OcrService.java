package com.flashbrain.service;

import com.flashbrain.dto.UploadResult;
import com.flashbrain.entity.Snippet;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OcrService {

    @Autowired
    private SnippetService snippetService;

    @Autowired
    private SnippetImageService snippetImageService;

    @Autowired
    private FileTextExtractor fileTextExtractor;

    // 默认读取系统环境变量，
    @Value("${ocr.baidu.apiKey}")
    private String baiduApiKey;

    @Value("${ocr.baidu.secretKey}")
    private String baiduSecretKey;

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    // 本地内存缓存，避免频繁向百度获取 token
    private String cachedToken = null;
    private long tokenExpiryTime = 0;

    public UploadResult uploadAndExtract(MultipartFile file, String snippetId, String userId, Long expectedOcrTextVersion) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new FileExtractException("上传文件不能为空");
        }

        byte[] fileBytes = file.getBytes();
        String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        FileKind kind = fileTextExtractor.detectKind(filename, file.getContentType(), fileBytes);
        if (kind == FileKind.UNKNOWN) {
            throw new FileExtractException("不支持的文件类型，请上传图片、txt、docx、doc、pdf 或 ofd 文件");
        }

        if (kind == FileKind.IMAGE) {
            return recognizeTextAsync(file, snippetId, userId);
        }

        snippetImageService.saveUploadedFile(snippetId, userId, file);
        String text = fileTextExtractor.extractText(kind, fileBytes, filename, this::recognizeText);
        snippetService.replaceOcrIfVersionMatches(snippetId, userId, text, expectedOcrTextVersion);
        return new UploadResult(
                ExtractStatus.TEXT_EXTRACTED.name(),
                snippetId,
                kind.name(),
                text,
                "文件内容已读取到 OCR 原文区"
        );
    }

    public UploadResult recognizeTextAsync(MultipartFile file, String snippetId, String userId) throws IOException {
        Snippet snippet = snippetService.getSnippet(snippetId, userId);
        Long expectedVersion = snippet.getOcrTextVersion();
        snippetImageService.saveImage(snippetId, userId, file);

        byte[] fileBytes = file.getBytes();
        String filename = file.getOriginalFilename();

        CompletableFuture.runAsync(() -> {
            try {
                String text = recognizeText(fileBytes, filename);
                boolean updated = snippetService.replaceOcrIfVersionStillMatches(snippetId, userId, text, expectedVersion);
                if (updated) {
                    log.info("OCR result saved for snippet: {}", snippetId);
                } else {
                    log.info("Skip OCR result for snippet: {} because OCR text was modified", snippetId);
                }
            } catch (Exception e) {
                log.error("Async OCR failed for snippet: {}", snippetId, e);
            }
        });

        return new UploadResult(
                ExtractStatus.OCR_PROCESSING.name(),
                snippetId,
                FileKind.IMAGE.name(),
                null,
                "图片上传成功，OCR 正在后台识别"
        );
    }

    public String recognizeText(MultipartFile file) {
        try {
            return recognizeText(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload file: " + e.getMessage());
        }
    }

    public String recognizeText(byte[] fileBytes, String filename) {
        log.info("Sending OCR request to Baidu Cloud for file: {}", filename);
        
        String token = getValidToken();
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("Failed to obtain Baidu OCR Access Token");
        }

        // 1. 将图片字节数据进行 Base64 编码
        String imgBase64 = Base64.getEncoder().encodeToString(fileBytes);

        // 2. 构造表单参数（FormBody.Builder 会自动对参数进行 URL 编码）
        RequestBody requestBody = new FormBody.Builder()
                .add("image", imgBase64)
                .build();

        // 3. 构建高精度识别接口请求 (accurate_basic)
        Request request = new Request.Builder()
                .url("https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic?access_token=" + token)
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject resJson = JSONUtil.parseObj(responseBody);
                
                // 处理百度返回的接口级错误
                if (resJson.containsKey("error_code")) {
                    throw new RuntimeException("Baidu API error: " + resJson.getStr("error_msg"));
                }
                
                // 4. 解析并合并文字结果
                JSONArray wordsResult = resJson.getJSONArray("words_result");
                if (wordsResult == null) {
                    return "";
                }
                
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < wordsResult.size(); i++) {
                    JSONObject item = wordsResult.getJSONObject(i);
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(item.getStr("words"));
                }
                return sb.toString();
            } else {
                throw new RuntimeException("Baidu OCR API HTTP error: " + response.code());
            }
        } catch (Exception e) {
            log.error("Failed to call Baidu OCR Service", e);
            throw new RuntimeException("OCR process failed: " + e.getMessage());
        }
    }

    /**
     * 获取有效的 Access Token (带本地内存缓存，防频繁网络鉴权请求)
     */
    private synchronized String getValidToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return cachedToken;
        }

        String token = fetchAccessTokenFromBaidu();
        if (token != null) {
            cachedToken = token;
            // 百度 token 默认 30 天有效，我们保守设置为 25 天失效
            tokenExpiryTime = System.currentTimeMillis() + 25L * 24 * 3600 * 1000;
        }
        return token;
    }

    /**
     * 向百度鉴权服务器请求 Access Token
     */
    private String fetchAccessTokenFromBaidu() {
        log.info("Fetching access token from Baidu cloud...");
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create("", mediaType);

        String url = String.format(
                "https://aip.baidubce.com/oauth/2.0/token?client_id=%s&client_secret=%s&grant_type=client_credentials",
                baiduApiKey, baiduSecretKey
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String resStr = response.body().string();
                JSONObject json = JSONUtil.parseObj(resStr);
                String token = json.getStr("access_token");
                if (token != null && !token.isEmpty()) {
                    return token;
                }
                log.error("Failed to parse access_token, response: {}", resStr);
            } else {
                log.error("Baidu token request failed, HTTP code: {}", response.code());
            }
        } catch (Exception e) {
            log.error("Network error when fetching Baidu token", e);
        }
        return null;
    }
}
