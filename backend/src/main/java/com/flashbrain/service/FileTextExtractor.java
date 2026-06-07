package com.flashbrain.service;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class FileTextExtractor {
    private static final int MAX_UPLOAD_BYTES = 30 * 1024 * 1024;
    private static final int MAX_PDF_PAGES = 50;
    private static final int MAX_OFD_XML_ENTRIES = 200;
    private static final int MAX_TEXT_LENGTH = 300_000;

    public FileKind detectKind(String filename, String contentType, byte[] fileBytes) {
        String extension = extensionOf(filename);
        if (isImageExtension(extension) || (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/"))) {
            return FileKind.IMAGE;
        }
        if ("txt".equals(extension)) {
            return FileKind.TXT;
        }
        if ("docx".equals(extension)) {
            return FileKind.DOCX;
        }
        if ("doc".equals(extension)) {
            return FileKind.DOC;
        }
        if ("pdf".equals(extension) || startsWith(fileBytes, "%PDF".getBytes(StandardCharsets.US_ASCII))) {
            return FileKind.PDF;
        }
        if ("ofd".equals(extension)) {
            return FileKind.OFD;
        }
        return FileKind.UNKNOWN;
    }

    public String extractText(FileKind kind, byte[] fileBytes, String filename, BiFunction<byte[], String, String> imageOcr) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new FileExtractException("上传文件不能为空");
        }
        if (fileBytes.length > MAX_UPLOAD_BYTES) {
            throw new FileExtractException("文件过大，请上传 30MB 以内的文件");
        }
        String text;
        try {
            switch (kind) {
                case TXT:
                    text = extractTxt(fileBytes);
                    break;
                case DOCX:
                    text = extractDocx(fileBytes);
                    break;
                case DOC:
                    text = extractDoc(fileBytes);
                    break;
                case PDF:
                    text = extractPdf(fileBytes, filename, imageOcr);
                    break;
                case OFD:
                    text = extractOfd(fileBytes);
                    break;
                default:
                    throw new FileExtractException("不支持的文件类型");
            }
        } catch (FileExtractException e) {
            throw e;
        } catch (Exception e) {
            throw new FileExtractException("文件内容解析失败: " + e.getMessage(), e);
        }
        text = normalizeText(text);
        if (StringUtils.isBlank(text)) {
            throw new FileExtractException("无法提取文件文本内容");
        }
        return limitText(text);
    }

    private String extractTxt(byte[] fileBytes) {
        try {
            return decode(fileBytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException ignored) {
            try {
                return decode(fileBytes, Charset.forName("GB18030"));
            } catch (CharacterCodingException e) {
                throw new FileExtractException("TXT 文件编码无法识别，请使用 UTF-8 或 GB18030 编码");
            }
        }
    }

    private String extractDocx(byte[] fileBytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDoc(byte[] fileBytes) throws Exception {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(fileBytes));
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractPdf(byte[] fileBytes, String filename, BiFunction<byte[], String, String> imageOcr) throws Exception {
        try (PDDocument document = PDDocument.load(fileBytes)) {
            if (document.isEncrypted()) {
                throw new FileExtractException("PDF 文件已加密，无法读取内容");
            }
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new FileExtractException("PDF 页数超过 " + MAX_PDF_PAGES + " 页，请拆分后上传");
            }

            String directText = new PDFTextStripper().getText(document);
            if (directText != null && !directText.trim().isEmpty()) {
                return directText;
            }

            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 180, ImageType.RGB);
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", out);
                    String pageText = imageOcr.apply(out.toByteArray(), filename + "-page-" + (i + 1) + ".png");
                    if (pageText != null && !pageText.trim().isEmpty()) {
                        text.append("\n--- 第 ").append(i + 1).append(" 页 ---\n").append(pageText.trim()).append('\n');
                    }
                }
            }
            return text.toString();
        }
    }

    private String extractOfd(byte[] fileBytes) throws Exception {
        StringBuilder text = new StringBuilder();
        int xmlEntries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    continue;
                }
                xmlEntries++;
                if (xmlEntries > MAX_OFD_XML_ENTRIES) {
                    throw new FileExtractException("OFD 文件结构过大，无法安全读取");
                }
                ByteArrayOutputStream xml = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    xml.write(buffer, 0, read);
                    if (xml.size() > 2 * 1024 * 1024) {
                        throw new FileExtractException("OFD XML 内容过大，无法安全读取");
                    }
                }
                collectOfdTextCode(xml.toByteArray(), text);
            }
        }
        if (xmlEntries == 0) {
            throw new FileExtractException("不是有效的 OFD 文件");
        }
        if (text.toString().trim().isEmpty()) {
            throw new FileExtractException("无法提取 OFD 文本，文件可能没有可读取的文本层");
        }
        return text.toString();
    }

    private void collectOfdTextCode(byte[] xmlBytes, StringBuilder text) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(new InputSource(new InputStreamReader(new ByteArrayInputStream(xmlBytes), StandardCharsets.UTF_8)));
        collectTextCodeNodes(document.getDocumentElement(), text);
    }

    private void collectTextCodeNodes(Node node, StringBuilder text) {
        if (node == null) {
            return;
        }
        String localName = node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
        if ("TextCode".equals(localName)) {
            String value = node.getTextContent();
            if (value != null && !value.trim().isEmpty()) {
                text.append(value.trim()).append('\n');
            }
            return;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectTextCodeNodes(children.item(i), text);
        }
    }

    private String decode(byte[] fileBytes, Charset charset) throws CharacterCodingException {
        String text = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(fileBytes))
                .toString();
        if (text.startsWith("﻿")) {
            return text.substring(1);
        }
        return text;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String limitText(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH) + "\n\n[内容过长，已截断到 " + MAX_TEXT_LENGTH + " 字符]";
    }

    private boolean isImageExtension(String extension) {
        return "jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension)
                || "bmp".equals(extension) || "webp".equals(extension);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
