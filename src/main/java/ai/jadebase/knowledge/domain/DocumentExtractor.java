package ai.jadebase.knowledge.domain;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DocumentExtractor {

    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "markdown", "csv");

    public String extract(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String extension = extension(filename);
        String content = switch (extension) {
            case "pdf" -> extractPdf(file);
            case "docx" -> extractDocx(file);
            default -> {
                if (!TEXT_EXTENSIONS.contains(extension)) {
                    throw new IllegalArgumentException("暂不支持该文件类型：" + extension + "。支持 PDF、DOCX、Markdown、TXT、CSV");
                }
                yield new String(file.getBytes(), StandardCharsets.UTF_8);
            }
        };
        String normalized = content.replace("\u0000", "").replaceAll("[ \\t]+", " ").trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("文档没有可提取的文本内容");
        return normalized;
    }

    private String extractPdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(MultipartFile file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
