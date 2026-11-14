package com.vaabhi.backup;

import java.util.List;

/** What one attachment pass copied, and the per-file problems to note on the run and retry next night. */
public record AttachmentCopyResult(int copied, List<String> problems) {}
