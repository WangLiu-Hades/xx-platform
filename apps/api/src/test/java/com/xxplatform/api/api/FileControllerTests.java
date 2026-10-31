package com.xxplatform.api.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 下载响应头的细节（中文文件名与空格不能乱码）。 */
class FileControllerTests {

    @Test
    void attachmentHeaderUsesRfc5987WithUtf8Name() {
        assertEquals("attachment; filename*=UTF-8''collect%20result.csv",
                FileController.attachment("collect result.csv"));
    }

    @Test
    void attachmentHeaderPercentEncodesChineseName() {
        String header = FileController.attachment("采集结果.xlsx");
        assertEquals("attachment; filename*=UTF-8''%E9%87%87%E9%9B%86%E7%BB%93%E6%9E%9C.xlsx", header);
    }

    @Test
    void attachmentHeaderFallsBackForMissingName() {
        assertEquals("attachment; filename*=UTF-8''download", FileController.attachment(null));
    }
}
