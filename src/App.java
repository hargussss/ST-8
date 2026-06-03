import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class App {

    private static final String BASE_URL = "http://www.papercdcase.com/";
    private static final Path DATA_FILE = Path.of("data", "data.txt");
    private static final Path RESULT_PDF = Path.of("result", "cd.pdf");

    public static void main(String[] args) throws Exception {
        CoverData data = loadCoverData(DATA_FILE);
        Files.createDirectories(RESULT_PDF.getParent());

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--allow-insecure-localhost");
        options.setAcceptInsecureCerts(true);

        WebDriver webDriver = new ChromeDriver(options);
        webDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(60));

        try {
            webDriver.get(BASE_URL);

            WebElement artistField = wait.until(
                    ExpectedConditions.presenceOfElementLocated(By.name("artist")));
            WebElement titleField = webDriver.findElement(By.name("title"));

            artistField.sendKeys(data.artist());
            titleField.sendKeys(data.title());

            List<String> tracks = data.tracks();
            for (int i = 0; i < tracks.size() && i < 16; i++) {
                WebElement trackField = webDriver.findElement(By.name("track" + (i + 1)));
                trackField.sendKeys(tracks.get(i));
            }

            WebElement jewelCase = webDriver.findElement(
                    By.xpath("//input[@name='template' and @value='jewel']"));
            jewelCase.click();

            WebElement a4Paper = webDriver.findElement(
                    By.xpath("//input[@name='size' and @value='a4']"));
            a4Paper.click();

            WebElement btn = webDriver.findElement(By.name("submit"));
            btn.submit();

            wait.until(driver -> driver.getCurrentUrl().contains(".pdf"));
            savePdf(webDriver.getCurrentUrl(), RESULT_PDF);
            System.out.println("PDF saved to " + RESULT_PDF.toAbsolutePath());
        } finally {
            webDriver.quit();
        }
    }

    private static CoverData loadCoverData(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();

        if (lines.size() < 2) {
            throw new IOException("data.txt must contain artist, title and optional tracks");
        }

        String artist = lines.get(0);
        String title = lines.get(1);
        List<String> tracks = new ArrayList<>();
        for (int i = 2; i < lines.size() && tracks.size() < 18; i++) {
            tracks.add(lines.get(i));
        }

        return new CoverData(artist, title, tracks);
    }

    private static void savePdf(String pdfUrl, Path destination) throws Exception {
        String downloadUrl = pdfUrl.replaceFirst("^https://", "http://");

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(
                request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download PDF, HTTP " + response.statusCode());
        }

        try (InputStream body = response.body()) {
            Files.copy(body, destination);
        }
    }

    private record CoverData(String artist, String title, List<String> tracks) {
    }
}
