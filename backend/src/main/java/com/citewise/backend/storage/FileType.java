package com.citewise.backend.storage;

public enum FileType {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg");

    private final String contentType;
    private final String extension;

    FileType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String getContentType() { return contentType; }
    public String getExtension() { return extension; }

    /** Looks at the first bytes of the file, never at the name or label the user gave it. */
    public static FileType detect(byte[] head) {
        if (startsWith(head, new byte[]{'%', 'P', 'D', 'F', '-'})) return PDF;
        if (startsWith(head, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) return PNG;
        if (startsWith(head, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) return JPEG;
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}