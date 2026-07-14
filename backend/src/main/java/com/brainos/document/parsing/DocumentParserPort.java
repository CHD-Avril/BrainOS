package com.brainos.document.parsing;

import java.nio.file.Path;
import java.util.List;

public interface DocumentParserPort {

    List<ParsedSection> parse(Path path, String mimeType);
}
