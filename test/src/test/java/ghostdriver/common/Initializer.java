package ghostdriver.test.common;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.net.URL;
import java.io.FileReader;
import java.util.Properties;


public class Initializer {
    /** Set of files from which to fetch the properties.
     */
    //@{
    /** File storing all supported drivers and their user-readable description as properties.
     * The contents of that file should be edited to reflect the user's setup.
     *
     *@see java.util.Properties
     */
    public static final String DRIVERS_LIST_FILE = "config/drivers.properties";
    
    /** File storing paths to all necessary executables as properties.
     * The contents of that file should be edited to reflect the user's setup.
     *
     *@see java.util.Properties
     */
    public static final String DRIVERS_PATHS_FILE = "config/paths.properties";
    
    /** Value appended to some properties list to get their default values.
     */
    public static final String DEFAULTS_EXTENSION = ".defaults";
    //@}
    
    /** Port used by GhostDriver.
     */
    public static int port = 8080;
    
    
    /** The desired capabilities for all drivers.
     */
    protected DesiredCapabilities desiredCapabilities;
    
    /** List of all drivers mapped to their user-readable description
     */
    protected Properties availableDrivers;
    
    /** Paths to all necessary executables.
     */
    protected Properties paths;

    
    /** Some drivers might need to start another process.
    */
    private Process serverProcess;
    
    
    protected Initializer() {
        try {
            availableDrivers = new Properties();
            availableDrivers.load(new FileReader(DRIVERS_LIST_FILE));
        } catch (Exception e) {
            throw new RuntimeException("Error while trying to load drivers list file (" + DRIVERS_LIST_FILE + ")", e);
        }
        
        try {
            paths = new Properties();
            paths.load(new FileReader(DRIVERS_PATHS_FILE + DEFAULTS_EXTENSION));
            try {
                paths.load(new FileReader(DRIVERS_PATHS_FILE));
            } catch (java.io.FileNotFoundException e) {
                System.err.println("\nAre you sure you don't need to override the default executables paths?\n"
                                   + "If tests fail, first make sure the '" + DRIVERS_PATHS_FILE + "' properties point at the proper executables on your system!\n");
                // no specific error recovery procedure
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while trying to load executable paths file (" + DRIVERS_PATHS_FILE + ")", e);
        }
        
        desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setJavascriptEnabled(true);
    }
    
    protected WebDriver setupGhostDriver() {
        WebDriver driver = null;
        try {
	        serverProcess = Runtime.getRuntime().exec(paths.getProperty("phantomjs") + ' ' + paths.getProperty("ghostdriver"));
	        
	        BufferedReader phantomOutputReader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));
	        while (phantomOutputReader.readLine () == null) { } //< As soon as Ghostdriver outputs something, we can continue
	        phantomOutputReader.close();
	        
    	    System.out.println("Remote WebDriver server port: " + port);    
	        driver = new RemoteWebDriver(new URL("http://localhost:" + port), desiredCapabilities);
        } catch (Exception e) {
        	cleanup(driver);
        	throw new RuntimeException(e);
        }
                
        return driver;
    }
    
    protected void cleanup(WebDriver driver) {
        // Ensure we don't leave zombies around...

        if (driver != null)
            driver.quit();
        
        if (serverProcess != null)
            serverProcess.destroy();
    }
}
