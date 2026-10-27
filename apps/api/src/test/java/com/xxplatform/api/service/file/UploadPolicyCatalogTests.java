package com.xxplatform.api.service.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 上传白名单目录的形状约束。
 *
 * <p>这些用例的价值不在"跑一遍 switch"，而在把契约里的类别/媒体对应关系钉住：
 * 如果哪天有人把 RAW_INGESTION 加进图片组、或给 REPORT 开了上传口子，这里会先红。
 */
class UploadPolicyCatalogTests {

    private static final long MAX = 10 * 1024 * 1024L;

    @Test
    void faceAndPortraitOnlyAcceptJpegAndPng() {
        for (FileCategory category : new FileCategory[]{FileCategory.FACE_SEARCH, FileCategory.PROFILE_PORTRAIT}) {
            UploadPolicyCatalog.UploadPolicy policy = UploadPolicyCatalog.policyFor(category, MAX);
            assertTrue(policy.allowsMime("image/jpeg"));
            assertTrue(policy.allowsMime("image/png"));
            assertFalse(policy.allowsMime("application/json"));
            assertTrue(policy.allowsExtension(".jpg"));
            assertTrue(policy.allowsExtension(".png"));
            assertFalse(policy.allowsExtension(".csv"));
            assertEquals(MAX, policy.maxBytes());
        }
    }

    @Test
    void importCategoriesAcceptXlsxCsvJsonOnly() {
        for (FileCategory category : new FileCategory[]{
                FileCategory.DATASET_IMPORT, FileCategory.DEVICE_IMPORT,
                FileCategory.PERSON_LIBRARY_IMPORT, FileCategory.RAW_INGESTION}) {
            UploadPolicyCatalog.UploadPolicy policy = UploadPolicyCatalog.policyFor(category, MAX);
            assertTrue(policy.allowsExtension(".xlsx"));
            assertTrue(policy.allowsExtension(".csv"));
            assertTrue(policy.allowsExtension(".json"));
            assertFalse(policy.allowsMime("image/png"));
            assertFalse(policy.allowsExtension(".zip"));
        }
    }

    @Test
    void serverGeneratedCategoriesRejectUploadPolicyLookup() {
        for (FileCategory category : new FileCategory[]{
                FileCategory.REPORT, FileCategory.EXPORT, FileCategory.CAPTURE_IMAGE}) {
            assertFalse(category.uploadable());
            assertThrows(IllegalArgumentException.class,
                    () -> UploadPolicyCatalog.policyFor(category, MAX));
        }
    }

    @Test
    void uploadableFlagMatchesContractEnum() {
        int uploadable = 0;
        for (FileCategory category : FileCategory.values()) {
            if (category.uploadable()) {
                uploadable++;
            }
        }
        assertEquals(6, uploadable, "契约 UploadFileRequest.category 是 6 个值");
    }

    @Test
    void contentTypesAreNormalizedBeforeComparison() {
        assertEquals("text/csv", UploadPolicyCatalog.normalizeMime("text/csv; charset=UTF-8"));
        assertEquals("application/json", UploadPolicyCatalog.normalizeMime(" Application/JSON "));
        assertEquals("", UploadPolicyCatalog.normalizeMime(null));
        assertEquals("", UploadPolicyCatalog.normalizeMime("  "));
    }

    @Test
    void extensionsAreLowercasedAndDefaultToEmpty() {
        assertEquals(".csv", UploadPolicyCatalog.extensionOf("采集结果.CSV"));
        assertEquals(".jpg", UploadPolicyCatalog.extensionOf("a.b.c.JPG"));
        assertEquals("", UploadPolicyCatalog.extensionOf("no-extension"));
        assertEquals("", UploadPolicyCatalog.extensionOf("trailing."));
        assertEquals("", UploadPolicyCatalog.extensionOf(null));
    }

    @Test
    void categoryParsingIsCaseInsensitiveAndRejectsUnknown() {
        assertEquals(FileCategory.FACE_SEARCH, FileCategory.parse("face_search"));
        assertEquals(FileCategory.RAW_INGESTION, FileCategory.parse(" RAW_INGESTION "));
        assertThrows(IllegalArgumentException.class, () -> FileCategory.parse("NOT_A_CATEGORY"));
        assertThrows(IllegalArgumentException.class, () -> FileCategory.parse("  "));
    }
}
