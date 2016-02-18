package histori.main.wiki;

import histori.main.HistoriApiOptions;
import histori.wiki.WikiArchive;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.args4j.Option;

import java.io.File;

public class ArticleImportOptions extends HistoriApiOptions {

    public static final String USAGE_WDIR = "Directory containing Wikipedia index and article files";
    public static final String OPT_WDIR = "-w";
    public static final String LONGOPT_WDIR= "--wiki-dir";
    @Option(name=OPT_WDIR, aliases=LONGOPT_WDIR, usage=USAGE_WDIR, required=true)
    @Getter @Setter private File wikiDir = null;

    @Getter(lazy=true) private final WikiArchive wiki = initArchive();
    private WikiArchive initArchive() { return new WikiArchive(wikiDir); }

    public static final String USAGE_FILE = "Input WikiArticle json file";
    public static final String OPT_FILE = "-i";
    public static final String LONGOPT_FILE= "--input-file";
    @Option(name=OPT_FILE, aliases=LONGOPT_FILE, usage=USAGE_FILE, required=true)
    @Getter @Setter private File file = null;

}