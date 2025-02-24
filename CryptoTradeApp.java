import io.aeron.Aeron;
import io.aeron.FragmentHandler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.Header;
import org.agrona.concurrent.UnsafeBuffer;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.crypto.Credentials;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

// Import your generated smart contract wrapper (generated via web3j)
// The contract should expose: executeTrade(String asset, String side, BigInteger quantity, BigInteger price)
import com.example.contracts.TradingContract;

import javax.swing.*;
import java.awt.*;
import java.math.BigInteger;
import java.util.concurrent.*;

public class CryptoTradeApp {

    // Ethereum & Contract Configuration (replace with your actual values)
    private static final String ETHEREUM_NODE_URL = "https://mainnet.infura.io/v3/YOUR_INFURA_KEY";
    private static final String PRIVATE_KEY = "YOUR_PRIVATE_KEY";
    private static final String CONTRACT_ADDRESS = "0xYourContractAddressHere";

    // UI Components
    private JFrame frame;
    private JTextField inputField;
    private JTextArea logArea;

    // Aeron Components
    private Publication publication;
    private Subscription subscription;
    private Aeron aeron;
    private MediaDriver mediaDriver;

    // Ethereum Components
    private Web3j web3j;
    private Credentials credentials;
    private TradingContract tradingContract;

    // Executors for asynchronous processing
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public CryptoTradeApp() {
        SwingUtilities.invokeLater(() -> {
            try {
                initUI();
                initAeron();
                initWeb3j();
                startSubscriptionPolling();
            } catch (Exception e) {
                log("Initialization error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // Initialize the Swing UI
    private void initUI() {
        frame = new JFrame("Crypto Trade App");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        inputField = new JTextField();
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        frame.setLayout(new BorderLayout());
        frame.add(inputField, BorderLayout.SOUTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        // When the user hits enter, process the command asynchronously.
        inputField.addActionListener(e -> {
            String command = inputField.getText().trim();
            inputField.setText("");
            executorService.submit(() -> processCommand(command));
        });

        frame.setVisible(true);
        log("UI initialized.");
    }

    // Log messages to the UI and console safely.
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        System.out.println(message);
    }

    // Set up Aeron: Launch an embedded MediaDriver and create a publication and subscription.
    private void initAeron() {
        try {
            mediaDriver = MediaDriver.launchEmbedded();
            Aeron.Context context = new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName());
            aeron = Aeron.connect(context);
            final String channel = "aeron:udp?endpoint=localhost:40123";
            final int streamId = 10;
            publication = aeron.addPublication(channel, streamId);
            subscription = aeron.addSubscription(channel, streamId);
            log("Aeron initialized on channel: " + channel);
        } catch (Exception e) {
            log("Error initializing Aeron: " + e.getMessage());
            throw e;
        }
    }

    // Initialize the Ethereum connection via web3j and load the trading contract.
    private void initWeb3j() {
        try {
            web3j = Web3j.build(new HttpService(ETHEREUM_NODE_URL));
            credentials = Credentials.create(PRIVATE_KEY);
            TransactionManager txManager = new RawTransactionManager(web3j, credentials);
            tradingContract = TradingContract.load(CONTRACT_ADDRESS, web3j, txManager, new DefaultGasProvider());
            log("Web3j initialized and contract loaded at: " + CONTRACT_ADDRESS);
        } catch (Exception e) {
            log("Error initializing Web3j: " + e.getMessage());
            throw e;
        }
    }

    // Start a scheduled task that polls Aeron's subscription for trade order messages.
    private void startSubscriptionPolling() {
        final FragmentHandler handler = (buf, offset, length, header) -> {
            try {
                byte[] data = new byte[length];
                buf.getBytes(offset, data);
                String message = new String(data);
                log("Received order: " + message);
                // Process the trade order asynchronously.
                executorService.submit(() -> processTradeOrder(message));
            } catch (Exception e) {
                log("Error in subscription handler: " + e.getMessage());
            }
        };

        scheduler.scheduleAtFixedRate(() -> {
            try {
                subscription.poll(handler, 10);
            } catch (Exception e) {
                log("Error polling Aeron subscription: " + e.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        log("Started Aeron subscription polling.");
    }

    // Process the user command from the UI.
    // Expected formats: "b 1 bitcoin" or "s 2 ethereum" (where "b" = buy and "s" = sell)
    private void processCommand(String command) {
        try {
            log("Processing command: " + command);
            String[] parts = command.split(" ");
            if (parts.length != 3) {
                log("Invalid command. Format: <b/s> <quantity> <asset>");
                return;
            }
            String side;
            if ("b".equalsIgnoreCase(parts[0])) {
                side = "BUY";
            } else if ("s".equalsIgnoreCase(parts[0])) {
                side = "SELL";
            } else {
                log("Invalid side. Use 'b' for buy or 's' for sell.");
                return;
            }
            double quantity;
            try {
                quantity = Double.parseDouble(parts[1]);
            } catch (NumberFormatException nfe) {
                log("Invalid quantity. Must be a number.");
                return;
            }
            String asset = parts[2].toUpperCase();

            // Lookup a static price for the asset (for demo purposes).
            double price;
            if ("BITCOIN".equals(asset)) {
                price = 30000.0;
            } else if ("ETHEREUM".equals(asset)) {
                price = 2000.0;
            } else {
                price = 1000.0; // Default price for unknown assets.
            }

            // Build the trade order message in the format:
            // "TRADE:<asset> <side> <quantity> <price>"
            String tradeOrder = "TRADE:" + asset + " " + side + " " + quantity + " " + price;
            log("Publishing trade order: " + tradeOrder);

            // Publish the trade order via Aeron with basic retry logic.
            UnsafeBuffer buffer = new UnsafeBuffer(tradeOrder.getBytes());
            long result;
            int retryCount = 0;
            do {
                result = publication.offer(buffer);
                if (result < 0L) {
                    Thread.sleep(100);
                    retryCount++;
                    if (retryCount > 50) {
                        log("Failed to publish trade order after several retries.");
                        return;
                    }
                }
            } while (result < 0L);
            log("Trade order published via Aeron.");
        } catch (Exception e) {
            log("Error processing command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Process the trade order message received from Aeron.
    // This method parses the order and then calls the Ethereum smart contract to execute the trade.
    private void processTradeOrder(String message) {
        try {
            // Expected format: "TRADE:<asset> <side> <quantity> <price>"
            String[] parts = message.split(" ");
            if (parts.length != 4 || !parts[0].startsWith("TRADE:")) {
                log("Invalid trade message format: " + message);
                return;
            }
            String asset = parts[0].substring(6); // Remove "TRADE:" prefix.
            String side = parts[1];
            double quantity = Double.parseDouble(parts[2]);
            double price = Double.parseDouble(parts[3]);

            // For demo: convert quantity and price into BigInteger values (simulate 18 decimals).
            BigInteger quantityWei = BigInteger.valueOf((long) (quantity * 1e18));
            BigInteger priceWei = BigInteger.valueOf((long) (price * 1e18));

            log("Executing trade on Ethereum: " + side + " " + quantity + " " + asset + " @ " + price);
            // Call the smart contract method asynchronously.
            TransactionReceipt receipt = tradingContract.executeTrade(asset, side, quantityWei, priceWei).send();
            log("Trade executed on blockchain. TxHash: " + receipt.getTransactionHash());
        } catch (Exception e) {
            log("Error executing trade on Ethereum: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Clean up resources when closing the app.
    private void shutdown() {
        try {
            if (aeron != null) aeron.close();
            if (mediaDriver != null) mediaDriver.close();
            if (web3j != null) web3j.shutdown();
            executorService.shutdown();
            scheduler.shutdown();
            log("Resources shut down successfully.");
        } catch (Exception e) {
            log("Error during shutdown: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        CryptoTradeApp app = new CryptoTradeApp();
        // Ensure resources are cleaned up on JVM exit.
        Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
    }
}
