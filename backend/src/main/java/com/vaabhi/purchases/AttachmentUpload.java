package com.vaabhi.purchases;

/** A key and a presigned URL the browser can put a bill's attachment to. */
public record AttachmentUpload(String key, String uploadUrl) {}
