package ghostdriver;

import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.UnreachableBrowserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.MalformedURLException;
import java.net.URL;
import java.io.FileReader;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;


public class GoogleCheese {
    /** Set of files from which to fetch the properties.
     */
    //@{
    private static final String DRIVERS_LIST_FILE = "drivers.properties"; // list of all drivers and their user-readable description
    
    private static final String DRIVERS_PATHS_FILE = "paths.properties"; // paths to all necessary executables; the contents of that file should be edited to reflect your setup
    //@}
    
    private static final int STATS_SAMPLING_SIZE = 100;
    
    
    private static Properties availableDrivers;
    private static Properties paths;

    
    private static void init() {
        try {
            availableDrivers =  new Properties();
            availableDrivers.load(new FileReader(DRIVERS_LIST_FILE));
        } catch (Exception e) {
            throw new RuntimeException("Error while trying to load drivers list file (" + DRIVERS_LIST_FILE + ")", e);
        }
        
        try {
            paths = new Properties();
            paths.load(new FileReader(DRIVERS_PATHS_FILE));
        } catch (Exception e) {
            throw new RuntimeException("Error while trying to load executable paths file (" + DRIVERS_PATHS_FILE + ")", e);
        }
    }
    
    public static void main(String[] args) throws MalformedURLException, IOException, InterruptedException {
        init();
        
        if (args.length == 0) {
            System.out.println("Testing all drivers and computing stats on them\n"
                               + "-----------------------------------------------\n"
                               + "If you simply want to test one driver, provide its name as argument.\n"
                               + "You can see and edit possible drivers in the .properties files along this example file.");
            runStats();
            
        } else {
            runTestWithDriver(args[0].toLowerCase());
        }

        System.exit(0);
    }
    
    public static long[][] runStats() {
        long stats[][] = new long[][]{{0, 0, 0}, {0, 0, 0}, {0, 0, 0}};
        byte driverCount = 0;

        for (String currentDriver : availableDrivers.stringPropertyNames()) {
            long sample[];
            
            try {
                // Sum up samples
                for (int i = 0; i < STATS_SAMPLING_SIZE; ++i) {
                    sample = runTestWithDriver(currentDriver);
                    stats[driverCount][0] += sample[0];
                    stats[driverCount][1] += sample[1];
                    stats[driverCount][2] += sample[2];
                }
                
                // Normalize
                stats[driverCount][0] /= STATS_SAMPLING_SIZE;
                stats[driverCount][1] /= STATS_SAMPLING_SIZE;
                stats[driverCount][2] /= STATS_SAMPLING_SIZE;
                
                // Display results
                System.out.println("");
                System.out.println("--------------------------------------");
                System.out.println("*** STATS for driver '" + currentDriver + "', averaged over " + STATS_SAMPLING_SIZE + " runs:");
                System.out.println("*** Driver start:\t" + (stats[driverCount][0] / 10E6) + " ms"); // divide to obtain ms from ns
                System.out.println("*** Test execution:\t" + (stats[driverCount][1] / 10E6) + " ms"); // divide to obtain ms from ns
                System.out.println("*** Total execution:\t" + (stats[driverCount][2] / 10E6) + " ms"); // divide to obtain ms from ns
                System.out.println("--------------------------------------");
                System.out.println("");
            } catch (Exception e) {
                System.err.println("Woops, an error occured while testing driver '" + currentDriver + "'!");
                e.printStackTrace();
            }
            
            // Go to next driver
            driverCount++;
        }
        
        return stats;
    }

    /**
     *@return   Array of nanoseconds durations: {timeToStartDriver, timeToTest, totalTime}, or `null` if the driver is not known.
     */
    public static long[] runTestWithDriver(String driverName) throws MalformedURLException, IOException, InterruptedException {
        String driverDescription = availableDrivers.getProperty(driverName);
        
        if (driverDescription == null) {
            System.err.println("Oops, driver '" + driverName + "' is unknown  :/");
            System.err.println("Take a look at the '" + DRIVERS_LIST_FILE + "' for a list of available drivers.");
            return null;
        }
        
        System.out.println("\n\n**********************\n"
                           + "Using " + driverDescription + "\n"
                           + "**********************");
        
        long timeToStartDriver,
             timeToTest,
             totalTime;
        
        // Ask for a JavaScript-enabled browser
        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setJavascriptEnabled(true);
        
        WebDriver driver = null;
        Capabilities actualCapabilities = null;
        Process phantomjsProcess = null;

        // Start the clock
        long startTime = System.nanoTime();
        

        if (driverName.equals("firefox")) {
            System.setProperty("webdriver.firefox.bin", paths.getProperty("firefox"));
            driver = new FirefoxDriver(capabilities);

            actualCapabilities = ((FirefoxDriver) driver).getCapabilities();
        } else if (driverName.equals("chrome")) {
            System.setProperty("webdriver.chrome.driver", paths.getProperty("chromedriver"));
            driver = new ChromeDriver();

            actualCapabilities = ((ChromeDriver) driver).getCapabilities();
        } else if (driverName.equals("ghost")) {
            phantomjsProcess = Runtime.getRuntime().exec(paths.getProperty("phantomjs") + ' ' + paths.getProperty("ghostdriver"));

            BufferedReader phantomOutputReader = new BufferedReader(new InputStreamReader(phantomjsProcess.getInputStream()));
            while (phantomOutputReader.readLine () == null) { } //< As soon as Ghostdriver outputs something, we can continue
            phantomOutputReader.close();

            try {
                driver = new RemoteWebDriver(new URL("http://localhost:8080"), capabilities);
                actualCapabilities = ((RemoteWebDriver) driver).getCapabilities();
            } catch (Exception e) {
                // Ensure we don't leave zombies around...
                phantomjsProcess.destroy();
                throw new RuntimeException(e);
            }
        }
        
        timeToStartDriver = System.nanoTime() - startTime;
        System.out.println("Driver started in (ns): " + timeToStartDriver);
        
        runTestOn(driver);
        
        if (phantomjsProcess != null)
            phantomjsProcess.destroy();

        totalTime = System.nanoTime() - startTime;
        timeToTest = totalTime - timeToStartDriver;
        System.out.println("Test done in (ns): " + timeToTest);
        System.out.println("Time elapsed (ns): " + totalTime);

        return new long[]{timeToStartDriver, timeToTest, totalTime};
    }
    
    private static void runTestOn(WebDriver driver) {
        // And now use this to visit Google
        System.out.println("Loading 'http://www.google.com'...");
        driver.get("http://www.google.com");
        System.out.println("Loaded. Current URL is: '" + driver.getCurrentUrl() + "'");
        
        // Find the text input element by its name
        System.out.println("Finding an Element via [name='q']...");
        WebElement element = driver.findElement(By.name("q"));
        System.out.println("Found.");
        
        // Enter something to search for
        System.out.println("Sending keys 'Cheese!...'");
        element.sendKeys("Cheese!");
        System.out.println("Sent.");
        
        // Now submit the form. WebDriver will find the form for us from the element
        System.out.println("Submitting Element...");
        element.submit();
        System.out.println("Submitted.");
        
        // Check the title of the page
        System.out.println("Page title is: " + driver.getTitle());
        driver.close();
    }
}
