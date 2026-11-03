package com.xxplatform.api.service.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 上传内容的<b>真解析</b>（M18）。
 *
 * <p>修复前只做"魔数级"检查：图片看 {@code ImageIO.read != null}、JSON 看能否
 * {@code readTree}、CSV 按行 {@code split(",")}、XLSX 只找 zip 里两个条目名。结果
 * {@code "this is not JSON"} 在分类允许 JSON 时也能拿到 ACTIVE；8 字节 PNG 头包住任意
 * 二进制也能通过；CSV 的引号内换行被当成列数不一致；10 万行上限完全没执行。
 *
 * <p>这里对四类内容分别做结构级校验，并<b>返回行数</b>供策略层执行上限：
 * <ul>
 *   <li>图片：用 ImageIO 读出真实格式名（JPEG/PNG）并与声明的 mime 比对，再完整解码；</li>
 *   <li>JSON：Jackson 解析成树，拒绝空文档与尾部多余内容；</li>
 *   <li>CSV：RFC 4180 状态机（引号、转义引号、引号内逗号/换行、CRLF），严格 UTF-8；</li>
 *   <li>XLSX：解 zip → 校验 [Content_Types].xml、xl/workbook.xml、workbook rels、
 *       每个 sheet 的 XML 均可解析，统计行数；拒绝 zip 炸弹与截断包。</li>
 * </ul>
 *
 * <p>本类只回答"内容是否可用、有多少行"，状态机与审计仍由 {@link FileService} 负责。
 */
public final class ContentValidator {

    /** XLSX 解压后的总量上限：既防 zip 炸弹，也避免把 100 MiB 导入放大成几个 GiB 堆占用。 */
    private static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;

    private static final Set<String> XLSX_MIME = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private ContentValidator() {
    }

    /** 校验结论：{@code valid=false} 进隔离；{@code tooManyRows=true} 按超限拒绝（413）。 */
    public record Result(boolean valid, boolean tooManyRows, long dataRows, String detail) {

        static Result ok(long rows) {
            return new Result(true, false, rows, "ok");
        }

        static Result invalid(String detail) {
            return new Result(false, false, 0, detail);
        }

        static Result tooManyRows(long rows) {
            return new Result(true, true, rows, "rows=" + rows);
        }
    }

    /**
     * 按分类与声明类型校验内容。
     *
     * @param category 分类（决定走哪条解析路径）
     * @param mime     已归一化的声明类型
     * @param content  完整字节
     * @param maxRows  行上限；0 表示不按行限制
     */
    public static Result validate(FileCategory category, String mime, byte[] content,
                                  int maxRows, ObjectMapper objectMapper) {
        return switch (category) {
            case FACE_SEARCH, PROFILE_PORTRAIT -> validateImage(mime, content);
            case DATASET_IMPORT, DEVICE_IMPORT, PERSON_LIBRARY_IMPORT, RAW_INGESTION ->
                    switch (mime) {
                        case "text/csv" -> validateCsv(content, maxRows);
                        case "application/json" -> validateJson(content, objectMapper);
                        default -> XLSX_MIME.contains(mime)
                                ? validateXlsx(content, maxRows)
                                : Result.invalid("该声明类型没有解析器：" + mime);
                    };
            default -> Result.invalid("该分类不接受客户端上传");
        };
    }

    // ------------------------------------------------------------------ 图片

    /**
     * 图片：先按真实格式名比对声明类型，再完整解码。
     *
     * <p>只调 {@code ImageIO.read} 不够：JPEG 解码器会忽略尾部的垃圾数据，"PNG 头 + 任意
     * 负载"也可能解出一个极小图。格式名比对能挡住"把 JPEG 改名成 .png 上传"这类绕过
     * 分类白名单的做法。
     */
    private static Result validateImage(String mime, byte[] content) {
        String expected = "image/jpeg".equals(mime) ? "jpeg" : "png";
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (in == null) {
                return Result.invalid("无法读取图片流");
            }
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return Result.invalid("没有可用的图片解码器");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName() == null
                        ? "" : reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.contains(expected)) {
                    return Result.invalid("图片实际格式 " + format + " 与声明 " + mime + " 不一致");
                }
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    return Result.invalid("图片尺寸非法：" + width + "x" + height);
                }
                // 完整解码一帧：尺寸合法但压缩流损坏的图片在这一步才会暴露。
                if (reader.read(0) == null) {
                    return Result.invalid("图片无法解码");
                }
                return Result.ok(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return Result.invalid("图片解码失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ JSON

    /** JSON：必须是单个合法文档；空白、尾部多余字符、坏语法都拒绝。 */
    private static Result validateJson(byte[] content, ObjectMapper objectMapper) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (IOException | RuntimeException e) {
            return Result.invalid("JSON 解析失败：" + e.getMessage());
        }
        if (root == null || root.isMissingNode()) {
            return Result.invalid("JSON 内容为空");
        }
        // 行数只对表格类有意义；JSON 导入的行数由导入任务按数组元素统计。
        return Result.ok(root.isArray() ? root.size() : 0);
    }

    // ------------------------------------------------------------------ CSV

    /**
     * CSV：RFC 4180 状态机。
     *
     * <p>规则：字段可被双引号包围；引号内的逗号与换行属于字段内容；{@code ""} 表示一个字面
     * 双引号；记录以 CRLF / LF / CR 结束。解析失败（未闭合引号、引号后出现非分隔字符、
     * 数据行列数与表头不一致、非法 UTF-8、NUL 字节）一律判无效。
     */
    private static Result validateCsv(byte[] content, int maxRows) {
        String text = decodeStrictUtf8(content);
        if (text == null) {
            return Result.invalid("CSV 不是合法 UTF-8");
        }
        if (text.indexOf('\u0000') >= 0) {
            return Result.invalid("CSV 含 NUL 字节");
        }
        List<Integer> fieldCounts = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        boolean quoteClosed = false;
        int fieldsInRecord = 0;
        boolean anyContent = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(c);
                }
                anyContent = true;
                continue;
            }
            if (c == '"') {
                if (fieldStarted && !field.isEmpty()) {
                    return Result.invalid("CSV 引号出现在字段中间");
                }
                if (quoteClosed) {
                    return Result.invalid("CSV 引号状态错误");
                }
                inQuotes = true;
                fieldStarted = true;
                anyContent = true;
                continue;
            }
            if (c == ',') {
                fieldsInRecord++;
                field.setLength(0);
                fieldStarted = false;
                quoteClosed = false;
                anyContent = true;
                continue;
            }
            if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                fieldsInRecord++;
                fieldCounts.add(fieldsInRecord);
                fieldsInRecord = 0;
                field.setLength(0);
                fieldStarted = false;
                quoteClosed = false;
                anyContent = true;
                continue;
            }
            if (quoteClosed) {
                return Result.invalid("CSV 引号后出现非分隔字符");
            }
            field.append(c);
            fieldStarted = true;
            anyContent = true;
        }
        if (inQuotes) {
            return Result.invalid("CSV 引号未闭合");
        }
        if (fieldsInRecord > 0 || fieldStarted) {
            fieldCounts.add(fieldsInRecord + 1);
        }
        if (!anyContent || fieldCounts.isEmpty()) {
            return Result.invalid("CSV 内容为空");
        }
        int headerColumns = fieldCounts.get(0);
        if (headerColumns < 1) {
            return Result.invalid("CSV 表头为空");
        }
        for (int i = 1; i < fieldCounts.size(); i++) {
            if (!fieldCounts.get(i).equals(headerColumns)) {
                return Result.invalid("CSV 第 " + (i + 1) + " 行列数为 " + fieldCounts.get(i)
                        + "，表头为 " + headerColumns);
            }
        }
        long dataRows = fieldCounts.size() - 1L;
        if (maxRows > 0 && dataRows > maxRows) {
            return Result.tooManyRows(dataRows);
        }
        return Result.ok(dataRows);
    }

    /** 严格 UTF-8 解码（非法字节直接失败，不做替换字符兜底）。 */
    private static String decodeStrictUtf8(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ XLSX

    /**
     * XLSX：完整走一遍 OPC 结构。
     *
     * <p>校验链：zip 能完整读出所有条目 → {@code [Content_Types].xml} 可解析 →
     * {@code xl/workbook.xml} 可解析且至少一个 sheet → {@code xl/_rels/workbook.xml.rels}
     * 可解析 → 每个 sheet 的目标 XML 存在且可解析 → 统计行数。
     *
     * <p>为什么不用 Apache POI：本项目 pom 没有该依赖，离线构建无法引入；而上传阶段只需要
     * 证明"这是一个结构完整的工作簿"，逐 sheet 的列语义属于导入任务。
     */
    private static Result validateXlsx(byte[] content, int maxRows) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        boolean sawContentTypes = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] bytes = readFully(zip);
                total += bytes.length;
                if (total > MAX_UNCOMPRESSED_BYTES) {
                    return Result.invalid("XLSX 解压后超过 " + MAX_UNCOMPRESSED_BYTES + " 字节上限");
                }
                entries.put(name, bytes);
                if ("[Content_Types].xml".equals(name)) {
                    sawContentTypes = true;
                }
            }
        } catch (IOException e) {
            return Result.invalid("XLSX 不是完整 zip：" + e.getMessage());
        }
        if (!sawContentTypes) {
            return Result.invalid("XLSX 缺少 [Content_Types].xml");
        }
        if (!entries.containsKey("xl/workbook.xml")) {
            return Result.invalid("XLSX 缺少 xl/workbook.xml");
        }
        Document contentTypes = parseXml(entries.get("[Content_Types].xml"));
        if (contentTypes == null) {
            return Result.invalid("[Content_Types].xml 不是合法 XML");
        }
        Document workbook = parseXml(entries.get("xl/workbook.xml"));
        if (workbook == null) {
            return Result.invalid("xl/workbook.xml 不是合法 XML");
        }
        NodeList sheets = workbook.getElementsByTagNameNS("*", "sheet");
        if (sheets.getLength() == 0) {
            return Result.invalid("工作簿没有任何 sheet");
        }
        Map<String, String> relationships = relationships(entries.get("xl/_rels/workbook.xml.rels"));
        long dataRows = 0;
        for (int i = 0; i < sheets.getLength(); i++) {
            Element sheet = (Element) sheets.item(i);
            String name = sheet.getAttribute("name");
            String rid = relationId(sheet);
            String target = rid.isEmpty() ? null : relationships.get(rid);
            if (target == null) {
                return Result.invalid("sheet " + name + " 缺少关系目标");
            }
            String path = resolveSheetPath(target);
            byte[] sheetBytes = entries.get(path);
            if (sheetBytes == null) {
                return Result.invalid("sheet " + name + " 的目标缺失：" + path);
            }
            Document sheetDoc = parseXml(sheetBytes);
            if (sheetDoc == null) {
                return Result.invalid("sheet " + name + " 不是合法 XML");
            }
            int rows = sheetDoc.getElementsByTagNameNS("*", "row").getLength();
            dataRows += Math.max(0, rows - 1L);
            if (dataRows > Integer.MAX_VALUE) {
                return Result.invalid("XLSX 行数溢出");
            }
        }
        // sharedStrings 存在就必须可解析（坏包常在这里暴露）。
        if (entries.containsKey("xl/sharedStrings.xml")
                && parseXml(entries.get("xl/sharedStrings.xml")) == null) {
            return Result.invalid("xl/sharedStrings.xml 不是合法 XML");
        }
        if (maxRows > 0 && dataRows > maxRows) {
            return Result.tooManyRows(dataRows);
        }
        return Result.ok(dataRows);
    }

    private static byte[] readFully(ZipInputStream zip) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String relationId(Element sheet) {
        // r:id 带命名空间；DOM 的 getAttribute 对带前缀属性名有效，但不同解析器会归一化前缀，
        // 因此按 localName 扫描一遍更稳。
        var attributes = sheet.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attr = attributes.item(i);
            if ("id".equals(attr.getLocalName()) || "id".equals(attr.getNodeName())) {
                return attr.getNodeValue();
            }
            if ("id".equals(attr.getLocalName()) && "r".equals(attr.getPrefix())) {
                return attr.getNodeValue();
            }
        }
        return "";
    }

    private static Map<String, String> relationships(byte[] relsBytes) {
        Map<String, String> result = new HashMap<>();
        Document rels = relsBytes == null ? null : parseXml(relsBytes);
        if (rels == null) {
            return result;
        }
        NodeList list = rels.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < list.getLength(); i++) {
            Element rel = (Element) list.item(i);
            String id = rel.getAttribute("Id");
            String target = rel.getAttribute("Target");
            if (!id.isEmpty() && !target.isEmpty()) {
                result.put(id, target);
            }
        }
        return result;
    }

    /** 关系目标可能是相对路径（worksheets/sheet1.xml）或绝对路径（/xl/worksheets/...）。 */
    private static String resolveSheetPath(String target) {
        String path = target.startsWith("/") ? target.substring(1) : "xl/" + target;
        while (path.contains("/../")) {
            path = path.replace("/../", "/");
        }
        return path;
    }

    private static Document parseXml(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new InputSource(new ByteArrayInputStream(bytes)));
        } catch (Exception e) {
            return null;
        }
    }
}
