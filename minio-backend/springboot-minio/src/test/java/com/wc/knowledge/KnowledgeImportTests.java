package com.wc.knowledge;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeImportTests {
    final KnowledgeImportService service = new KnowledgeImportService();
    MockMultipartFile file(String name, byte[] data) { return new MockMultipartFile("file", name, "application/octet-stream", data); }
    static byte[] docx(String body) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(body.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return out.toByteArray();
    }
    static String xml(String body) {
        return "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + body + "</w:body></w:document>";
    }
    byte[] pdf(int pages, boolean content, boolean encrypted) throws IOException {
        try (var doc = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (int i = 1; i <= pages; i++) {
                var page = new PDPage(); doc.addPage(page);
                if (content && i == 1) try (var stream = new PDPageContentStream(doc, page)) {
                    stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(30, 700); stream.showText("Deadline: September 30. Bring student ID."); stream.endText();
                }
            }
            if (encrypted) doc.protect(new StandardProtectionPolicy("owner", "secret", new AccessPermission()));
            doc.save(output); return output.toByteArray();
        }
    }
    @Test void txtPreservesParagraphsAndCleansBomAndFilename() {
        var result = service.extract(file("C:\\fakepath\\通知.txt", "\uFEFF报名材料\r\n学生证。".getBytes(StandardCharsets.UTF_8)));
        assertEquals("通知", result.suggestedTitle()); assertEquals("报名材料\n学生证。", result.content());
        assertEquals(0, result.pages()); assertFalse(result.warnings().isEmpty());
    }
    @Test void invalidUtf8BinaryAndUnsupportedTypesFail() {
        assertThrows(KnowledgeImportService.ImportFailure.class, () -> service.extract(file("bad.txt", new byte[]{(byte)0xff})));
        assertThrows(KnowledgeImportService.ImportFailure.class, () -> service.extract(file("bad.txt", new byte[]{1, 2, 3})));
        assertEquals(415, assertThrows(KnowledgeImportService.ImportFailure.class,
                () -> service.extract(file("old.doc", new byte[]{1}))).status.value());
        assertThrows(KnowledgeImportService.ImportFailure.class, () -> service.extract(file("fake.pdf", "not PDF".getBytes())));
    }
    @Test void fileAndTextLimitsRejectWithoutSilentTruncation() {
        assertEquals(413, assertThrows(KnowledgeImportService.ImportFailure.class,
                () -> service.extract(file("large.txt", new byte[102401]))).status.value());
        assertEquals(413, assertThrows(KnowledgeImportService.ImportFailure.class,
                () -> service.extract(file("large.pdf", new byte[KnowledgeImportService.MAX_FILE + 1]))).status.value());
        assertTrue(assertThrows(KnowledgeImportService.ImportFailure.class,
                () -> service.extract(file("long.txt", "字".repeat(20001).getBytes(StandardCharsets.UTF_8)))).getMessage().contains("不会自动截断"));
        assertThrows(KnowledgeImportService.ImportFailure.class, () -> service.extract(file("empty.txt", new byte[0])));
    }
    @Test void pdfTextAndEmptyPageWarningArePreserved() throws Exception {
        var result = service.extract(file("Notice.pdf", pdf(2, true, false)));
        assertEquals(2, result.pages()); assertTrue(result.content().contains("student ID"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("1 页没有可提取文字")));
    }
    @Test void scannedEncryptedAndTooManyPagesFailClearly() throws Exception {
        assertTrue(assertThrows(KnowledgeImportService.ImportFailure.class,
                () -> service.extract(file("scan.pdf", pdf(1, false, false)))).getMessage().contains("OCR"));
        assertTrue(assertThrows(KnowledgeImportService.ImportFailure.class,
                () -> service.extract(file("locked.pdf", pdf(1, true, true)))).getMessage().contains("加密"));
        assertTrue(assertThrows(KnowledgeImportService.ImportFailure.class,
                () -> service.extract(file("long.pdf", pdf(101, false, false)))).getMessage().contains("100"));
    }
    @Test void docxParagraphsTablesAndRevisionsKeepTextOrder() throws Exception {
        String body = "<w:p><w:r><w:t>报名</w:t><w:tab/><w:t>材料</w:t></w:r></w:p>"
                + "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>学生证</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
                + "<w:del><w:p><w:r><w:delText>作废内容</w:delText></w:r></w:p></w:del>"
                + "<w:p><w:ins><w:r><w:t>最新要求</w:t></w:r></w:ins></w:p>";
        var result = service.extract(file("通知.docx", docx(xml(body))));
        assertEquals("报名\t材料\n学生证\n最新要求", result.content());
        assertFalse(result.content().contains("作废"));
    }
    @Test void docxCannotResolveExternalEntities() throws Exception {
        String body = "<!DOCTYPE w:document [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]>"
                + xml("<w:p><w:r><w:t>&secret;</w:t></w:r></w:p>");
        assertThrows(KnowledgeImportService.ImportFailure.class, () -> service.extract(file("entity.docx", docx(body))));
    }
    @Test void docxInflationDepthAndOutputAreBounded() throws Exception {
        assertThrows(KnowledgeImportService.ImportFailure.class, () -> service.extract(file("bomb.docx", docx("x".repeat(KnowledgeImportService.MAX_INFLATED + 1)))));
        assertThrows(KnowledgeImportService.ImportFailure.class, () -> service.extract(file("deep.docx", docx(xml("<w:p>".repeat(130) + "</w:p>".repeat(130))))));
        assertThrows(KnowledgeImportService.ImportFailure.class, () -> service.extract(file("long.docx", docx(xml("<w:p><w:r><w:t>" + "字".repeat(20001) + "</w:t></w:r></w:p>")))));
    }
    @Test void failuresReleaseSlotForSubsequentImports() {
        for (int i = 0; i < 5; i++) {
            assertThrows(KnowledgeImportService.ImportFailure.class, () -> service.extract(file("bad.pdf", new byte[]{1})));
            assertEquals("okay", service.extract(file("good.txt", "okay".getBytes())).content());
        }
    }
}
