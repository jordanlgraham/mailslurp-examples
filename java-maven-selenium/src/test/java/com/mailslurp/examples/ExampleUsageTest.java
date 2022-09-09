package com.mailslurp.examples;

import com.mailslurp.apis.InboxControllerApi;
import com.mailslurp.apis.WaitForControllerApi;
import com.mailslurp.clients.ApiClient;
import com.mailslurp.clients.ApiException;
import com.mailslurp.clients.Configuration;
import com.mailslurp.models.Email;
import com.mailslurp.models.Inbox;
import java.io.File;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Example Selenium test-suite that loads a dummy authentication website
 * and tests user sign-up with an email verification process
 *
 * See https://www.mailslurp.com/examples/ for more information.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ExampleUsageTest {
    // website useful for testing, has a real authentication flow
    private static final String PLAYGROUND_URL = "https://playground.mailslurp.com";

    // get a MailSlurp API Key free at https://app.mailslurp.com
    private static final String YOUR_API_KEY = System.getenv("API_KEY");

    private static final String WEBDRIVER_PATH = System.getenv("PATH_TO_WEBDRIVER");
    private static final Boolean UNREAD_ONLY = true;
    private static final Long TIMEOUT_MILLIS = 30000L;

    private static ApiClient mailslurpClient;
    private static WebDriver driver;

    private static final String TEST_PASSWORD = "password-" + new Random().nextLong();

    private static Inbox inbox;
    private static Email email;
    private static String confirmationCode;

    /**
     * Setup selenium webdriver and MailSlurp client (for fetching emails)
     */
    @BeforeClass
    public static void beforeAll() {
        assertNotNull(YOUR_API_KEY);
        assertNotNull(WEBDRIVER_PATH);

        // setup mailslurp
        mailslurpClient = Configuration.getDefaultApiClient();
        mailslurpClient.setApiKey(YOUR_API_KEY);
        mailslurpClient.setConnectTimeout(TIMEOUT_MILLIS.intValue());

        // setup webdriver (expects geckodriver binary at WEBDRIVER_PATH)
        assertTrue(new File(WEBDRIVER_PATH).exists());
        System.setProperty("webdriver.gecko.driver", WEBDRIVER_PATH);
        driver = new FirefoxDriver();
        driver.manage().timeouts().implicitlyWait(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * Load the playground site in selenium
     */
    @Test
    public void test1_canLoadAuthenticationPlayground() {
        driver.get(PLAYGROUND_URL);
        assertEquals(driver.getTitle(), "React App");
    }

    /**
     * Start the sign-up process
     */
    @Test
    public void test2_canClickSignUpButton() {
        driver.findElement(By.cssSelector("[data-test=sign-in-create-account-link]")).click();
    }

    //<gen>selenium_maven_inbox
    /**
     * Create a real email address with MailSlurp and use it to start sign-up on the playground
     */
    @Test
    public void test3_canCreateEmailAddressAndSignUp() throws ApiException {
        // create a real, randomized email address with MailSlurp to represent a user
        InboxControllerApi inboxControllerApi = new InboxControllerApi(mailslurpClient);
        inbox = inboxControllerApi.createInbox(null,null,null,null,null,null,null, null, null);

        // check the inbox was created
        assertNotNull(inbox.getId());
        assertTrue(inbox.getEmailAddress().contains("@mailslurp.com"));

        // fill the playground app's sign-up form with the MailSlurp
        // email address and a random password
        driver.findElement(By.name("email")).sendKeys(inbox.getEmailAddress());
        driver.findElement(By.name("password")).sendKeys(TEST_PASSWORD);

        // submit the form to trigger the playground's email confirmation process
        // we will need to receive the confirmation email and extract a code
        driver.findElement(By.cssSelector("[data-test=sign-up-create-account-button]")).click();
    }
    //</gen>

    /**
     * Use MailSlurp to receive the confirmation email that is sent by playground
     */
    @Test
    public void test4_canReceiveConfirmationEmail() throws ApiException {
        // receive a verification email from playground using mailslurp
        WaitForControllerApi waitForControllerApi = new WaitForControllerApi(mailslurpClient);
        email = waitForControllerApi.waitForLatestEmail(inbox.getId(), TIMEOUT_MILLIS, UNREAD_ONLY);

        // verify the contents
        assertTrue(email.getSubject().contains("Please confirm your email address"));
    }

    /**
     * Extract the confirmation code from email body using regex pattern
     */
    @Test
    public void test5_canExtractConfirmationCodeFromEmail() {
        // create a regex for matching the code we expect in the email body
        Pattern p = Pattern.compile(".*verification code is (\\d+).*");
        Matcher matcher = p.matcher(email.getBody());

        // find first occurrence and extract
        assertTrue(matcher.find());
        confirmationCode = matcher.group(1);

        assertTrue(confirmationCode.length() == 6);
    }

    /**
     * Submit the confirmation code to the playground to confirm the user
     */
    @Test
    public void test6_canSubmitVerificationCodeToPlayground() {
        driver.findElement(By.name("code")).sendKeys(confirmationCode);
        driver.findElement(By.cssSelector("[data-test=confirm-sign-up-confirm-button]")).click();
    }

    /**
     * Test sign-in as confirmed user
     */
    @Test
    public void test7_canLoginWithConfirmedUser() {
        // load the main playground login page
        driver.get(PLAYGROUND_URL);

        // login with now confirmed email address
        driver.findElement(By.name("username")).sendKeys(inbox.getEmailAddress());
        driver.findElement(By.name("password")).sendKeys(TEST_PASSWORD);
        driver.findElement(By.cssSelector("[data-test=sign-in-sign-in-button]")).click();

        // verify that user can see authenticated content
        assertTrue(driver.findElement(By.tagName("h1")).getText().contains("Welcome"));
    }

    /**
     * After tests close selenium
     */
    @AfterClass
    public static void afterAll() {
        driver.close();
    }

}
