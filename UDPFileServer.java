import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPFileServer {

    public static void main(String[] args) {
        System.out.println("Ready to receive!");
        int port = 1234; // Change this to the desired port
        String serverRoute = "/Users/yiren/Documents/m4/src/main/java"; // Change this to the desired directory route (Where the file will be stored)
        // all files will be stored under this route
        createFile(port, serverRoute); // Creating the file
    }

    public static void createFile(int port, String directoryPath) { // Handling data reception  by creating a file
        try {
            // Establish a socket for receiving data
            DatagramSocket datagramSocket = new DatagramSocket(port);
            byte[] bufferForFileName = new byte[1024]; // Buffer for the incoming file name
            DatagramPacket packetForFileName = new DatagramPacket(bufferForFileName, bufferForFileName.length);

            // Receive the packet containing the file's name
            datagramSocket.receive(packetForFileName);
            System.out.println("File name is being received");

            // Extracting the file name from the received packet
            byte[] receivedData = packetForFileName.getData();
            String receivedFileName = new String(receivedData, 0, packetForFileName.getLength());

            System.out.println("Initiating file creation");
            File fileToCreate = new File(directoryPath + File.separator + receivedFileName); // File object creation
            FileOutputStream fileOutputStream = new FileOutputStream(fileToCreate); // Stream to write file content

            // Start the process of file reception
            receiveFile(fileOutputStream, datagramSocket);
        } catch (Exception exception) {
            exception.printStackTrace();
            System.exit(1);
        }
    }


    public static void receiveFile(FileOutputStream fileOutput, DatagramSocket dataSocket) throws IOException { // Keep receiving data and send ACK if correct
        System.out.println("File reception in progress");
        boolean endOfFileFlag; // Indicator for end of file
        int currentSequenceNumber = 0; // Tracking sequence order
        int lastConfirmedSequence = 0; // The last sequence confirmed

        while (true) {
            byte[] receivedDataBuffer = new byte[1024]; // Buffer for incoming data
            byte[] fileDataBuffer = new byte[1021]; // Buffer for file data

            // Receiving the data packet
            DatagramPacket incomingPacket = new DatagramPacket(receivedDataBuffer, receivedDataBuffer.length);
            dataSocket.receive(incomingPacket);
            receivedDataBuffer = incomingPacket.getData();

            // Address and port for ACK
            InetAddress senderAddress = incomingPacket.getAddress();
            int senderPort = incomingPacket.getPort();

            // Getting the sequence number from the packet
            currentSequenceNumber = ((receivedDataBuffer[0] & 0xff) << 8) + (receivedDataBuffer[1] & 0xff);
            // Checking if it's the last packet
            endOfFileFlag = (receivedDataBuffer[2] & 0xff) == 1;

            // Verify the sequence number
            if (currentSequenceNumber == (lastConfirmedSequence + 1)) {
                // Update the last confirmed sequence number
                lastConfirmedSequence = currentSequenceNumber;

                // Extracting file data from the packet
                System.arraycopy(receivedDataBuffer, 3, fileDataBuffer, 0, 1021);

                // Write data to file and log the received sequence
                fileOutput.write(fileDataBuffer);
                System.out.println("Received: Sequence number: " + lastConfirmedSequence);

                // Acknowledge the received packet
                sendAck(lastConfirmedSequence, dataSocket, senderAddress, senderPort);
            } else {
                // Log unexpected sequence and resend ACK for the last confirmed sequence
                System.out.println("Expected sequence number: " + (lastConfirmedSequence + 1) + " but received " + currentSequenceNumber + ". DISCARDING");
                sendAck(lastConfirmedSequence, dataSocket, senderAddress, senderPort);
            }

            // Check if it's the last packet to end the loop
            if (endOfFileFlag) {
                fileOutput.close();
                break;
            }
        }
    }


    //get address, port, API, and last packet's seq num
    // send ACK when the content is correct
    private static void sendAck(int lastSequenceReceived, DatagramSocket dataSocket, InetAddress destinationAddress, int destinationPort) throws IOException {
        // Transmitting acknowledgement
        // Enhancing the sequence number range
        byte[] ackData = new byte[2];
        ackData[0] = (byte) (lastSequenceReceived >> 8);
        ackData[1] = (byte) lastSequenceReceived;
        // Preparing the packet for transmission
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, destinationAddress, destinationPort);
        dataSocket.send(ackPacket);
        System.out.println("Acknowledgement sent: Sequence Number = " + lastSequenceReceived);
    }

}
