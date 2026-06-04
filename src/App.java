import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
public class App {

    private static final String BASE_URL = "http://www.papercdcase.com/";
    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath();
    private static final Path DATA_FILE = PROJECT_ROOT.resolve("data/data.txt");
    private static final Path RESULT_DIR = PROJECT_ROOT.resolve("result");
    private static final Path RESULT_PDF = RESULT_DIR.resolve("cd.pdf");

    public static void main(String[] args) throws Exception {
        CdCoverData data = CdCoverData.load(DATA_FILE);
        RESULT_DIR.toFile().mkdirs();
        if (Files.exists(RESULT_PDF)) {
            Files.delete(RESULT_PDF);
        }

        ChromeOptions options = new ChromeOptions();
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", RESULT_DIR.toString());
        prefs.put("download.prompt_for_download", false);
        prefs.put("plugins.always_open_pdf_externally", true);
        options.setExperimentalOption("prefs", prefs);

        WebDriver webDriver = new ChromeDriver(options);
        try {
            webDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            webDriver.get(BASE_URL);

            WebElement artistField = webDriver.findElement(By.xpath("//input[@name='artist']"));
            WebElement titleField = webDriver.findElement(By.xpath("//input[@name='title']"));
            artistField.sendKeys(data.artist());
            titleField.sendKeys(data.title());

            for (int i = 0; i < data.tracks().size() && i < 16; i++) {
                String trackName = "track" + (i + 1);
                WebElement trackField = webDriver.findElement(By.xpath("//input[@name='" + trackName + "']"));
                trackField.sendKeys(data.tracks().get(i));
            }

            WebElement paperA4 = webDriver.findElement(By.xpath("//input[@name='size' and @value='a4']"));
            paperA4.click();

            WebElement jewelCase = webDriver.findElement(By.xpath("//input[@name='template' and @value='jewel']"));
            jewelCase.click();

            WebElement forceSaveAs = webDriver.findElement(By.xpath("//input[@name='force_saveas']"));
            forceSaveAs.click();

            WebElement btn = webDriver.findElement(By.xpath("//input[@name='submit']"));
            btn.submit();

            waitForPdfDownload(RESULT_DIR);
            Path downloaded = findLatestPdf(RESULT_DIR);
            if (downloaded == null) {
                throw new IllegalStateException("No PDF file found in " + RESULT_DIR);
            }
            if (!downloaded.equals(RESULT_PDF)) {
                Files.move(downloaded, RESULT_PDF, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("PDF saved to " + RESULT_PDF);
        } finally {
            webDriver.quit();
        }
    }

    private static void waitForPdfDownload(Path directory) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (findLatestPdf(directory) != null) {
                Thread.sleep(500);
                if (!hasPartialDownload(directory)) {
                    return;
                }
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("PDF download timed out in " + directory);
    }

    private static boolean hasPartialDownload(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.anyMatch(path -> path.getFileName().toString().endsWith(".crdownload"));
        }
    }

    private static Path findLatestPdf(Path directory) throws IOException {
        List<Path> pdfs;
        try (var stream = Files.list(directory)) {
            pdfs = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .collect(Collectors.toList());
        }
        if (pdfs.isEmpty()) {
            return null;
        }
        Path latest = pdfs.get(0);
        for (Path pdf : pdfs) {
            if (Files.getLastModifiedTime(pdf).compareTo(Files.getLastModifiedTime(latest)) > 0) {
                latest = pdf;
            }
        }
        return latest;
    }

    private static final class CdCoverData {
        private final String artist;
        private final String title;
        private final List<String> tracks;

        private CdCoverData(String artist, String title, List<String> tracks) {
            this.artist = artist;
            this.title = title;
            this.tracks = tracks;
        }

        String artist() {
            return artist;
        }

        String title() {
            return title;
        }

        List<String> tracks() {
            return tracks;
        }

        static CdCoverData load(Path path) throws IOException {
            List<String> lines = Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
            if (lines.size() < 3) {
                throw new IllegalArgumentException("data.txt must contain artist, title and at least one track");
            }
            String artist = lines.get(0);
            String title = lines.get(1);
            List<String> tracks = lines.subList(2, Math.min(lines.size(), 20));
            if (tracks.size() > 18) {
                tracks = tracks.subList(0, 18);
            }
            return new CdCoverData(artist, title, tracks);
        }
    }
}
