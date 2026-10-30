package com.citewise.backend.storage;

public record StoredFile(String storedFilename, FileType type, long size) {}