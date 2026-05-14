package revisionapp.core;

import revisionapp.model.StudyModule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportService {
    private final AppPaths paths;

    public ExportService(AppPaths paths) {
        this.paths = paths;
    }

    public void exportProgressZip(Path target) throws IOException {
        zipDirectory(paths.progress(), target);
    }

    public void exportFullBackup(Path target) throws IOException {
        zipDirectory(paths.root(), target);
    }

    public void exportDefinitionsPdf(Path target, StudyModule module, List<StudyModule.Definition> definitions) throws IOException {
        List<String> pages = layoutDefinitions(module, definitions, "Definitions");
        writePdf(target, pages);
    }

    public void exportGlossaryPdf(Path target, StudyModule module, List<StudyModule.Definition> definitions) throws IOException {
        List<String> pages = layoutDefinitions(module, definitions, "Glossary");
        writePdf(target, pages);
    }

    private void zipDirectory(Path source, Path target) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            if (!Files.exists(source)) {
                return;
            }
            try (var stream = Files.walk(source)) {
                for (Path path : stream.filter(Files::isRegularFile).toList()) {
                    ZipEntry entry = new ZipEntry(source.relativize(path).toString().replace('\\', '/'));
                    zip.putNextEntry(entry);
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private List<String> layoutDefinitions(StudyModule module, List<StudyModule.Definition> definitions, String title) {
        List<StringBuilder> pageBuilders = new ArrayList<>();
        pageBuilders.add(new StringBuilder());
        int page = 0;
        double columnX = 46;
        double y = 792;
        int column = 0;

        y = pdfLine(pageBuilders.get(page), 46, y, 16, module.name + " - " + title + " - " + LocalDate.now());
        y -= 12;

        String lastTopic = "";
        for (StudyModule.Definition definition : definitions) {
            if (y < 72) {
                if (column == 0) {
                    column = 1;
                    columnX = 318;
                    y = 792;
                } else {
                    pageBuilders.add(new StringBuilder());
                    page++;
                    column = 0;
                    columnX = 46;
                    y = 792;
                }
            }

            if (!definition.topic.equals(lastTopic)) {
                lastTopic = definition.topic;
                y = pdfLine(pageBuilders.get(page), columnX, y, 10, module.categoryFor(definition.topic).label.toUpperCase());
                y -= 4;
            }
            y = pdfLine(pageBuilders.get(page), columnX, y, 11, definition.term);
            for (String line : wrap(definition.def, 43)) {
                y = pdfLine(pageBuilders.get(page), columnX, y, 9, line);
            }
            y -= 8;
        }

        for (int i = 0; i < pageBuilders.size(); i++) {
            pdfLine(pageBuilders.get(i), 270, 28, 8, "Page " + (i + 1));
        }
        return pageBuilders.stream().map(StringBuilder::toString).toList();
    }

    private double pdfLine(StringBuilder builder, double x, double y, int size, String text) {
        builder.append("BT /F1 ").append(size).append(" Tf ")
                .append((int) x).append(' ').append((int) y)
                .append(" Td (").append(escapePdf(sanitize(text))).append(") Tj ET\n");
        return y - (size + 5);
    }

    private List<String> wrap(String text, int limit) {
        List<String> lines = new ArrayList<>();
        String remaining = sanitize(text);
        while (remaining.length() > limit) {
            int split = remaining.lastIndexOf(' ', limit);
            if (split < 12) {
                split = limit;
            }
            lines.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        if (!remaining.isBlank()) {
            lines.add(remaining);
        }
        return lines;
    }

    private void writePdf(Path target, List<String> pages) throws IOException {
        List<byte[]> objects = new ArrayList<>();
        int pagesObject = 2;
        int fontObject = 3;
        int firstPageObject = 4;

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            kids.append(firstPageObject + i * 2).append(" 0 R ");
        }

        objects.add("<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.ISO_8859_1));
        objects.add(("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>")
                .getBytes(StandardCharsets.ISO_8859_1));
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>".getBytes(StandardCharsets.ISO_8859_1));

        for (int i = 0; i < pages.size(); i++) {
            int pageObject = firstPageObject + i * 2;
            int contentObject = pageObject + 1;
            byte[] stream = pages.get(i).getBytes(StandardCharsets.ISO_8859_1);
            objects.add(("<< /Type /Page /Parent " + pagesObject + " 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /Font << /F1 " + fontObject + " 0 R >> >> /Contents "
                    + contentObject + " 0 R >>").getBytes(StandardCharsets.ISO_8859_1));
            objects.add(("<< /Length " + stream.length + " >>\nstream\n" + pages.get(i) + "endstream")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.write(("%d 0 obj\n".formatted(i + 1)).getBytes(StandardCharsets.ISO_8859_1));
            out.write(objects.get(i));
            out.write("\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        int xref = out.size();
        out.write(("xref\n0 " + (objects.size() + 1) + "\n").getBytes(StandardCharsets.ISO_8859_1));
        out.write("0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
        for (int offset : offsets) {
            out.write(("%010d 00000 n \n".formatted(offset)).getBytes(StandardCharsets.ISO_8859_1));
        }
        out.write(("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF").getBytes(StandardCharsets.ISO_8859_1));
        Files.write(target, out.toByteArray());
    }

    private String escapePdf(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("→", "->")
                .replace("¬", "not ")
                .replace("∧", "and")
                .replace("∨", "or")
                .replace("⊥", "false")
                .replace("⊨", "|=")
                .replace("⊢", "|-")
                .replace("∀", "forall")
                .replace("∃", "exists")
                .replace("–", "-")
                .replace("—", "-")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("’", "'");
    }
}
