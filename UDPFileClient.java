import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import javax.swing.JFileChooser;

public class UDPFileClient {

    /*
        Method for getting the file to be send and send its name
     */
    private void prepareFileTransmission(int destinationPort, String destinationHost) {

        System.out.println("Selecting file for transmission");
        try {
            DatagramSocket transmissionSocket = new DatagramSocket();
            InetAddress destinationAddress = InetAddress.getByName(destinationHost);
            String selectedFileName;

            JFileChooser fileChooser = new JFileChooser(); // Interface for file selection
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY); // Restrict selection to files only
            if (fileChooser.isMultiSelectionEnabled()) { // Ensure only single file selection
                fileChooser.setMultiSelectionEnabled(false);
            }

            int selectionStatus = fileChooser.showOpenDialog(null);
            if (selectionStatus == JFileChooser.APPROVE_OPTION) { // Confirm file selection
                File selectedFile = fileChooser.getSelectedFile();
                selectedFileName = selectedFile.getName();
                byte[] fileNameData = selectedFileName.getBytes(); // Convert file name to byte array
                DatagramPacket fileNamePacket = new DatagramPacket(fileNameData, fileNameData.length, destinationAddress, destinationPort); // Packet for file name
                transmissionSocket.send(fileNamePacket); // Dispatch the file name

                byte[] fileContentBytes = convertFileToBytes(selectedFile); // Convert file to byte array
                transmitFile(transmissionSocket, fileContentBytes, destinationAddress, destinationPort); // Process for sending the file
            }
            transmissionSocket.close();
        } catch (Exception exception) {
            exception.printStackTrace();
            System.exit(1);
        }
    }


    private void transmitFile(DatagramSocket dataSocket, byte[] dataBytes, InetAddress targetAddress, int targetPort) throws IOException {
        System.out.println("Initiating file transmission");
        int packetSequenceNumber = 0; // Tracking packet order
        boolean isLastPacket; // Flag for the last packet
        int confirmedAckSequence = 0; // To track if the packet was acknowledged correctly

        for (int offset = 0; offset < dataBytes.length; offset += 1021) { //offset here s for content size; every loop stands for one packet
            packetSequenceNumber++;

            // Constructing the packet
            byte[] packetData = new byte[1024]; // Control data occupies the first three bytes
            packetData[0] = (byte) (packetSequenceNumber >> 8);
            packetData[1] = (byte) packetSequenceNumber;

            // Determining if this is the last packet
            if ((offset + 1021) >= dataBytes.length) {
                isLastPacket = true;
                packetData[2] = (byte) 1; // Marking this as the final packet
            } else {
                isLastPacket = false;
                packetData[2] = (byte) 0;
            }

            // Copying file data to the packet
            int length = isLastPacket ? dataBytes.length - offset : 1021;
            System.arraycopy(dataBytes, offset, packetData, 3, length);

            DatagramPacket outgoingPacket = new DatagramPacket(packetData, packetData.length, targetAddress, targetPort);
            dataSocket.send(outgoingPacket);
            System.out.println("Packet sent: Sequence number = " + packetSequenceNumber);

            boolean ackReceived; // Flag to check if ack was received

            while (true) {
                byte[] ackBuffer = new byte[2];
                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

                try {
                    dataSocket.setSoTimeout(50);
                    dataSocket.receive(ackPacket); //receive the ack from server side
                    confirmedAckSequence = ((ackBuffer[0] & 0xff) << 8) + (ackBuffer[1] & 0xff);
                    ackReceived = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Waiting for ack timed out");
                    ackReceived = false;
                }

                // If ack is for the correct packet, proceed
                if (ackReceived && (confirmedAckSequence == packetSequenceNumber)) {
                    System.out.println("Ack confirmed: Sequence Number = " + confirmedAckSequence);
                    break;
                } else {
                    // Resend the packet if ack is not received or incorrect
                    dataSocket.send(outgoingPacket);
                    System.out.println("Resending packet: Sequence Number = " + packetSequenceNumber);
                }
            }

            if (isLastPacket) {
                break;
            }
        }
    }


    private static byte[] convertFileToBytes(File targetFile) {
        // Initialize FileInputStream to null
        FileInputStream fileStream = null;
        // Initialize byte array based on file size
        // Casting file size from long to int
        byte[] fileBytes = new byte[(int) targetFile.length()];
        try {
            // Open stream for file reading
            fileStream = new FileInputStream(targetFile);
            // Read file contents into byte array
            fileStream.read(fileBytes);
            // Close the file stream
            fileStream.close();
        } catch (IOException ioException) {
            // Handle any I/O exceptions
            ioException.printStackTrace();
        }
        return fileBytes;
    }


    public static void main(String[] args) {
        int port = 1234;
        String host = "127.0.0.1"; // local host
        UDPFileClient fs = new UDPFileClient();
        fs.prepareFileTransmission(port, host);
    }
}
