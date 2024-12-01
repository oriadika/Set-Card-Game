**Set Card Game - Multithreaded Java Implementation**

**Overview**
This project is a multithreaded implementation of the Set Card Game, developed in Java as part of an assignment focused on concurrency, synchronization, and game logic design. The game is a simplified version of the original Set card game, where players compete to find valid sets of cards on a shared table.


**Game Highlights:**
  Multithreaded Players: Each player operates on a separate thread, interacting with the game concurrently.
  Dynamic Gameplay: Players place tokens on cards to form sets, validated by a dealer thread.
  Penalty and Rewards: Players receive points for valid sets and penalties for invalid ones.
  Auto-Reshuffle: The dealer reshuffles cards periodically if no valid sets are available.
  Human and AI Players: The game supports human players via keyboard input and AI players simulated through random actions.

  
**How to Compile and Run**
Prerequisites
  Java 11 or higher installed.
  Maven installed for dependency management and building the project.


**Compilation:**
1.Clone the repository:
  bash
  Copy code
  git clone https://github.com/yourusername/set-card-game.git
  cd set-card-game
2.Compile the project using Maven:
bash
Copy code
  mvn compile


**Running the Game**
1.Run the game using Maven:
  bash
  Copy code
  mvn exec:java
2.Follow the on-screen instructions for gameplay.
  Use the keyboard keys mapped to the card slots (e.g., Q, W, E, etc.) to place or remove tokens.


**Testing:**
Run unit tests to verify functionality:
  bash
  Copy code
  mvn test

  
**Features and Gameplay Logic**
**Card Rules**: A valid set consists of three cards where each feature (color, shape, number, shading) is either the same or different across all cards.
**Scoring**: Players earn points for identifying valid sets and are temporarily frozen for invalid sets.
**Game Flow**: The dealer manages the game flow, including card reshuffling and point updates.
**Concurrency**: Designed with efficient thread management and synchronization to handle concurrent player actions and shared resources.
