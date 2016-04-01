package histori.main.wiki;

import histori.wiki.WikiArchive;
import lombok.Cleanup;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.main.MainBase;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class WikiTitleIndexMain extends MainBase<WikiTitleIndexOptions> {

    public static final String TITLE_OPEN = "<title>";
    public static final String TITLE_CLOSE = "</title>";

    public static void main (String[] args) { main(WikiTitleIndexMain.class, args); }

    @Override protected void run() throws Exception {
        final WikiTitleIndexOptions opts = getOptions();
        String line;
        @Cleanup final BufferedReader reader = new BufferedReader(new InputStreamReader(opts.getStream()));
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            int pos = line.indexOf("<title>");
            if (pos != -1) {
                line = line.substring(pos + TITLE_OPEN.length());
                StringUtil.chop(line, TITLE_CLOSE);
                line = line.trim();
                final String path = WikiArchive.getArticlePath(line);
                if (path != null) out(line+"\t"+path);
            }
        }
    }
}
