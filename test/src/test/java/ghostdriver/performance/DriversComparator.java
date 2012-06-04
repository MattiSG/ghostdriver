package ghostdriver.test.performance;

import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.UnreachableBrowserException;


public abstract class DriversComparator extends ghostdriver.test.common.Initializer {
    
    /** Number of iterations when computing statistics.
     */
    public static final int STATS_SAMPLING_SIZE = 10;
    
    /**
     * Usage: [driverName [port]]
     * Where `diverName` is one of the drivers listed in the `DRIVERS_LIST_FILE` properties file.
     * If `driverName` refers to GhostDriver, then the optional `port` argument may be used to override the default.
     */
    protected WebDriverTester(String[] args) {
        if (args.length == 0) {
            System.out.println("Testing all drivers and computing stats on them\n"
                               + "-----------------------------------------------\n"
                               + "If you simply want to test one driver, provide its name as argument.\n"
                               + "You can see and edit possible drivers in the .properties files along this example file.");

            long[][] stats = runStats();
            
            printStats(stats);
        } else {
            timeTestWithDriver(args[0].toLowerCase());
            
            if (args.length > 1)
                port = Integer.parseInt(args[1]);
        }

        System.exit(0);
    }
    
    private long[][] runStats() {
        long stats[][] = new long[][]{{0, 0, 0}, {0, 0, 0}, {0, 0, 0}};
        byte driverCount = 0;

        for (String currentDriver : availableDrivers.stringPropertyNames()) {
            long sample[];
            
            try {
                // Sum up samples
                for (int i = 0; i < STATS_SAMPLING_SIZE; ++i) {
                    sample = timeTestWithDriver(currentDriver);
                    stats[driverCount][0] += sample[0];
                    stats[driverCount][1] += sample[1];
                    stats[driverCount][2] += sample[2];
                }
                
                // Normalize
                stats[driverCount][0] /= STATS_SAMPLING_SIZE;
                stats[driverCount][1] /= STATS_SAMPLING_SIZE;
                stats[driverCount][2] /= STATS_SAMPLING_SIZE;
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
     *@see  #runStats()
     */
    private void printStats(long[][] data) {
        byte driverCount = 0;
        
        for (String currentDriver : availableDrivers.stringPropertyNames()) {
            System.out.println("");
            System.out.println("--------------------------------------");
            System.out.println("*** STATS for driver '" + currentDriver + "', averaged over " + STATS_SAMPLING_SIZE + " runs:");
            System.out.println("*** Driver start:\t" + (data[driverCount][0] / 10E5) + " ms"); // divide to obtain ms from ns
            System.out.println("*** Test execution:\t" + (data[driverCount][1] / 10E5) + " ms"); // divide to obtain ms from ns
            System.out.println("*** Total execution:\t" + (data[driverCount][2] / 10E5) + " ms"); // divide to obtain ms from ns
            System.out.println("--------------------------------------");
            System.out.println("");
            
            driverCount++;
        }
    }

    /**
     *@return   Array of nanoseconds durations: {timeToStartDriver, timeToTest, totalTime}, or `null` if the driver is not known.
     */
    protected long[] timeTestWithDriver(String driverName) {
        String driverDescription = availableDrivers.getProperty(driverName);
                
        System.out.println("\n\n**********************\n"
                           + "Using " + driverDescription + "\n"
                           + "**********************");
        
        WebDriver driver;
        
        long timeToStartDriver,
             timeToTest,
             totalTime;
        
        // Start the clock
        long startTime = System.nanoTime();
        

        if (driverName.equals("firefox")) {
            driver = setupFirefoxDriver();
        } else if (driverName.equals("chrome")) {
            driver = setupChromeDriver();
        } else if (driverName.equals("ghost")) {
            driver = setupGhostDriver();
        } else {
            System.err.println("Oops, driver '" + driverName + "' is unknown  :/");
            System.err.println("Take a look at the '" + DRIVERS_LIST_FILE + "' for a list of available drivers.");
            
            return null;	
        }
        
        timeToStartDriver = System.nanoTime() - startTime;
        System.out.println("Driver started in (ns): " + timeToStartDriver);
        
        try {
            runTestOn(driver);
        } finally {
            cleanup(driver);
        }

        totalTime = System.nanoTime() - startTime;
        timeToTest = totalTime - timeToStartDriver;
        System.out.println("Test done in (ns): " + timeToTest);
        System.out.println("Time elapsed (ns): " + totalTime);

        return new long[]{timeToStartDriver, timeToTest, totalTime};
    }
    
    protected WebDriver setupFirefoxDriver() {
        String firefoxBinary = paths.getProperty("firefox");
    	if (firefoxBinary != null)
	        System.setProperty("webdriver.firefox.bin", firefoxBinary);

        WebDriver driver = new FirefoxDriver(desiredCapabilities);
        
        return driver;
    }
    
    protected WebDriver setupChromeDriver() {
        System.setProperty("webdriver.chrome.driver", paths.getProperty("chromedriver"));
        
        String chromeBinary = paths.getProperty("chrome");
        if (chromeBinary != null)
            desiredCapabilities.setCapability("chrome.binary", chromeBinary);
        
        WebDriver driver = new ChromeDriver(desiredCapabilities);
        
        return driver;
    }
    
    protected abstract void runTestOn(WebDriver driver);
}
