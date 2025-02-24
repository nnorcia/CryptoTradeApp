# CryptoTradeApp
Crypto Trade App (Java and Aeron)

# CryptoTradeApp

CryptoTradeApp is a demonstration application that integrates ultra-low latency messaging with blockchain-based trading. The app combines Aeron for high-performance messaging, Ethereum smart contract interactions via web3j, and a Swing-based user interface. Designed as a proof-of-concept for advanced crypto trading systems, it showcases asynchronous processing, robust error handling, and clean shutdown procedures.

## Features

- **Ultra-Low Latency Messaging:**  
  Utilizes Aeron with an embedded MediaDriver to deliver fast and reliable messaging. Trade orders are published and subscribed on a dedicated UDP channel, simulating high-frequency trading scenarios.

- **Blockchain Integration:**  
  Leverages web3j to interact with Ethereum smart contracts. The app calls a deployed contract method (`executeTrade`) to execute trades on-chain, converting trade parameters into appropriate Ethereum units.

- **User Interface:**  
  A simple Swing-based UI allows users to input trade commands (e.g., `b 1 bitcoin` for a buy order) and view real-time log output of the processing steps and blockchain transaction status.

- **Asynchronous & Robust:**  
  Incorporates asynchronous processing using ExecutorService and ScheduledExecutorService to keep the UI responsive while handling messaging and blockchain calls concurrently. Enhanced error handling and retry logic ensure system stability.

## Prerequisites

- **Java 8+**  
- **Maven or Gradle** for dependency management  
- **Aeron:** Include Aeron libraries in your project  
- **Web3j:** For Ethereum blockchain integration  
- **Smart Contract Wrapper:** Generate the Java wrapper for your Solidity contract (assumed here as `TradingContract`) using web3j tools  
- An Ethereum node access (e.g., [Infura](https://infura.io/))

## Configuration

Before running the app, update the following placeholders in the source code:

- `YOUR_INFURA_KEY` with your Infura project key.
- `YOUR_PRIVATE_KEY` with your Ethereum private key.
- `0xYourContractAddressHere` with your deployed smart contract address.

## Running the App

1. **Build the project** using your preferred build tool (Maven/Gradle).
2. **Run the main class:**  
   ```bash
   java -cp target/your-jar-with-dependencies.jar CryptoTradeApp
