package com.wc.knowledge;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.stream.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Read-only extraction. Original uploads and extracted text are never persisted here. */
@Service
public class KnowledgeImportService {
    static final int MAX_FILE = 5 * 1024 * 1024, MAX_TEXT = 20000, MAX_INFLATED = 16 * 1024 * 1024;
    private final Semaphore slots = new Semaphore(2);
    public record Preview(String format, String suggestedTitle, String content, int pages, List<String> warnings) {}
    public static class ImportFailure extends RuntimeException {
        final HttpStatus status;
        ImportFailure(HttpStatus status, String message) { super(message); this.status = status; }
    }
    static ImportFailure invalid(String message) { return new ImportFailure(HttpStatus.UNPROCESSABLE_ENTITY, message); }

    public Preview extract(MultipartFile file) {
        String filename = file == null ? "" : Objects.toString(file.getOriginalFilename(), "");
        String suffix = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("txt", "pdf", "docx").contains(suffix))
            throw new ImportFailure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "支持 UTF-8 TXT、文字型 PDF 和 DOCX；旧版 DOC 请先另存为 DOCX");
        int limit = suffix.equals("txt") ? 100 * 1024 : MAX_FILE;
        if (file.isEmpty()) throw invalid("文件为空，请选择包含正文的资料");
        if (file.getSize() > limit) throw tooLarge();
        if (!slots.tryAcquire()) throw new ImportFailure(HttpStatus.TOO_MANY_REQUESTS, "当前正在处理其他资料，请稍后再试");
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw tooLarge();
            List<String> warnings = new ArrayList<>();
            String content;
            int pages = 0;
            switch (suffix) {
                case "pdf" -> {
                    if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
                        throw invalid("文件内容不是有效 PDF");
                    try (var pdf = Loader.loadPDF(bytes)) {
                        if (pdf.isEncrypted()) throw invalid("请先在本机解密 PDF，再导入可提取文字的副本");
                        pages = pdf.getNumberOfPages();
                        if (pages == 0 || pages > 100) throw invalid("PDF 须为 1–100 页，请拆分后导入");
                        var text = new LimitedWriter(MAX_TEXT);
                        var stripper = new PDFTextStripper();
                        stripper.setSortByPosition(true);
                        int emptyPages = 0;
                        for (int page = 1; page <= pages; page++) {
                            var part = new LimitedWriter(MAX_TEXT - text.length());
                            stripper.setStartPage(page); stripper.setEndPage(page);
                            stripper.writeText(pdf, part);
                            String value = part.toString().strip();
                            if (value.isEmpty()) emptyPages++;
                            else { if (text.length() > 0) text.write("\n\n"); text.write(value); }
                        }
                        content = text.toString();
                        if (emptyPages > 0) warnings.add(emptyPages + " 页没有可提取文字，可能是扫描页或图片；这些页未导入，请先做 OCR 或补录正文。");
                    }
                    warnings.add("PDF 分栏、表格及换行可能影响阅读顺序，请对照原文件核对数字、日期和段落。");
                }
                case "docx" -> {
                    content = extractDocx(bytes);
                    warnings.add("仅提取 Word 正文及表格文字，保留插入内容、忽略已删除修订；图片、页眉页脚、脚注和批注不作为正文导入，请对照原文件补齐。");
                }
                default -> {
                    content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                }
            }
            content = content.replace("\r\n", "\n").replace('\r', '\n');
            if (content.startsWith("\uFEFF")) content = content.substring(1);
            content = content.strip();
            if (content.isEmpty()) throw invalid("没有提取到正文；扫描件或纯图片请先做 OCR，再导入文字");
            if (content.length() > MAX_TEXT) throw invalid("正文超过 20000 字，请按章节拆分后导入，不会自动截断");
            if (content.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
                throw invalid("正文包含非文本控制字符，请检查文件编码或另存为纯文本");
            String base = filename.replace('\\', '/'); base = base.substring(base.lastIndexOf('/') + 1);
            base = base.substring(0, base.lastIndexOf('.')).replaceAll("[\\p{Cntrl}]", "").strip();
            if (base.length() > 160) base = base.substring(0, Character.isHighSurrogate(base.charAt(159)) ? 159 : 160);
            warnings.add("提取结果尚未保存；请核对正文并补充发布单位、版本和有效日期。检索引用按保存后的正文段落定位。");
            return new Preview(suffix, base, content, pages, List.copyOf(warnings));
        } catch (ImportFailure e) { throw e; }
        catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) { throw invalid("PDF 已加密，请先在本机解密后导入"); }
        catch (CharacterCodingException e) { throw invalid("TXT 须使用 UTF-8 编码，请转换后再导入"); }
        catch (IOException | XMLStreamException | IllegalArgumentException e) { throw invalid("无法提取此文件，请检查文件是否损坏，或另存为 TXT / PDF / DOCX 后重试"); }
        finally { slots.release(); }
    }

    private static ImportFailure tooLarge() {
        return new ImportFailure(HttpStatus.PAYLOAD_TOO_LARGE, "文件过大：TXT 最多 100 KB，PDF / DOCX 最多 5 MB");
    }

    private static String extractDocx(byte[] bytes) throws IOException, XMLStreamException {
        byte[] document = null;
        int total = 0, entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 512) throw invalid("Word 文件包含过多内部文件，请简化或另存为纯文本");
                boolean body = entry.getName().equals("word/document.xml");
                if (body && document != null) throw invalid("Word 正文结构重复，请重新保存文件");
                var part = body ? new ByteArrayOutputStream() : null;
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_INFLATED) throw invalid("Word 解压后内容过大，请移除图片或按章节拆分");
                    if (body) part.write(buffer, 0, count);
                }
                if (body) document = part.toByteArray();
            }
        }
        if (document == null) throw invalid("文件不是包含正文的 DOCX 文档");
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicId, systemId, baseURI, namespace) -> { throw new XMLStreamException("External entity rejected"); });
        var reader = factory.createXMLStreamReader(new ByteArrayInputStream(document));
        var text = new LimitedWriter(MAX_TEXT);
        int depth = 0, deleted = 0;
        boolean inText = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) throw invalid("Word 包含不支持的 XML 声明");
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (++depth > 128) throw invalid("Word 正文结构过深，请另存为纯文本");
                    if (!wordNamespace(reader.getNamespaceURI())) continue;
                    String name = reader.getLocalName();
                    if (name.equals("del") || name.equals("moveFrom")) deleted++;
                    if (deleted > 0) continue;
                    if (name.equals("t")) inText = true;
                    else if (name.equals("tab")) text.write("\t");
                    else if (name.equals("br") || name.equals("cr")) text.write("\n");
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    if (!wordNamespace(reader.getNamespaceURI())) continue;
                    String name = reader.getLocalName();
                    if (name.equals("del") || name.equals("moveFrom")) deleted--;
                    if (name.equals("t")) inText = false;
                    if (deleted == 0 && name.equals("p")) text.write("\n");
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) && inText && deleted == 0) {
                    text.write(reader.getText());
                }
            }
        } finally { reader.close(); }
        return text.toString();
    }

    private static boolean wordNamespace(String namespace) {
        return "http://schemas.openxmlformats.org/wordprocessingml/2006/main".equals(namespace)
                || "http://purl.oclc.org/ooxml/wordprocessingml/main".equals(namespace);
    }

    private static class LimitedWriter extends Writer {
        private final StringBuilder text = new StringBuilder();
        private final int limit;
        LimitedWriter(int limit) { this.limit = limit; }
        public void write(char[] chars, int offset, int count) {
            if (text.length() + count > limit) throw invalid("正文超过 20000 字，请按章节拆分后导入，不会自动截断");
            text.append(chars, offset, count);
        }
        int length() { return text.length(); }
        public void flush() {}
        public void close() {}
        public String toString() { return text.toString(); }
    }
}
