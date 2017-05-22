import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.Random;

/**
 * Created by Vincent on 5/21/2017.
 */
public class UdpClient {
    private final static String SERVER_NAME = "codebank.xyz";
    private final static int PORT_NUMBER = 38005;
    private final int MIN_PACKET_SIZE = 20;
    private final int HEADER_SIZE = 12;
    String serverName;
    int portNumber;
    byte[] byteArray;

    public UdpClient(String serverName, int portNumber) {
        this.serverName = serverName;
        this.portNumber = portNumber;

        callServer();

    }

    private void callServer() {
        Integer bytes = 2;
        int i = 0;

        try (Socket socket = new Socket(serverName, portNumber)) {

            System.out.println("Connected to server");

            InputStream is = socket.getInputStream();
            InputStreamReader isr = new InputStreamReader(is, "UTF-8");
            BufferedReader br = new BufferedReader(isr);
            OutputStream os = socket.getOutputStream();
            PrintStream out = new PrintStream(os, true, "UTF-8");

            byte[] handShake = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
            int portNum = 0;

            os.write(getIpv4Packet(handShake));

            System.out.print("Handshake response: 0x");
            for (int j = 0; j < 4; j++)
                System.out.print(Integer.toHexString(is.read() & 0xFF).toUpperCase());
            portNum = ((is.read() << 8) & 0xFF00) | is.read();
            System.out.println("\nPort number received: " + portNum);

            double totalTime = 0;

            byte[] responseArray = new byte[4];
            while (i < 12) {
                double timeSent = System.currentTimeMillis(), timeReceived = 0, roundTripTime = 0;

                os.write(getIpv4Packet(getUdpPacket(portNum, bytes)));
                System.out.println("\nSending packet with " + bytes + " bytes of data");
                System.out.print("Response: 0x");
                for (int j = 0; j < responseArray.length; j++) {
                    responseArray[j] = (byte) is.read();
                    timeReceived = System.currentTimeMillis();
                    System.out.print(Long.toHexString(responseArray[j] & 0xFF).toUpperCase());
                }
                roundTripTime = timeReceived - timeSent;
                totalTime += roundTripTime;
                System.out.println("\nRTT:" + roundTripTime + "ms\n");
                i++;
                bytes *= 2;
            }

            System.out.println("Average RTT: " + new DecimalFormat("#.00").format(totalTime / 12) + "ms");

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private byte[] getUdpPacket(int destination, int size) {

        Random random = new Random();
        byte[] udpPacket = new byte[8 + size];

        udpPacket[0] = 0;
        udpPacket[1] = 0;


        udpPacket[2] = (byte) (destination >> 8); // destination (0xFF)
        udpPacket[3] = (byte) destination;        // destination (0xFF00)


        udpPacket[4] = (byte) (size >> 8);  //udp length 0xFF
        udpPacket[5] = (byte) size;         //udp length 0xFF00

        udpPacket[6] = 0;
        udpPacket[7] = 0;

        // data
        for (int i = 8; i < size + 8; i++)
            udpPacket[i] = (byte) random.nextInt();



        byte[] udpHeader = new byte[HEADER_SIZE];       //udp header
        udpHeader[0] = (byte) 12;
        udpHeader[1] = (byte) 13;
        udpHeader[2] = (byte) 14;
        udpHeader[3] = (byte) 15;


        udpHeader[4] = (byte) 52;   // dest addr  52.37.88.154
        udpHeader[5] = (byte) 37;
        udpHeader[6] = (byte) 88;
        udpHeader[7] = (byte) 154;

        udpHeader[8] = 0;

        udpHeader[9] = 17;                  //protocol
        udpHeader[10] = udpPacket[4];
        udpHeader[11] = udpPacket[5];


        byte[] checkSumByteArr = new byte[8 + size + HEADER_SIZE];   // checksum

        for (int i = 0; i < 8 + size; i++)
                checkSumByteArr[i] = udpPacket[i];
        for(int i = 0 ; i < udpHeader.length; i++)
                checkSumByteArr[i + 8 + size] = udpHeader[i];

        short checksum = checkSum(checkSumByteArr);
        udpPacket[6] = (byte) ((checksum >> 8) & 0xff);
        udpPacket[7] = (byte) (checksum & 0xff);

        return udpPacket;
    }

    private byte[] getIpv4Packet(byte[] additionalData) {
        byte[] ipv4Packet = new byte[MIN_PACKET_SIZE + additionalData.length];
        short length = (short) (MIN_PACKET_SIZE + additionalData.length);
        int i = 0;

        ipv4Packet[i++] = 0x45; //version  i = 0

        ipv4Packet[i++] = 0;  //tos     i = 1

        ipv4Packet[i++] = (byte) ((length >> 8) & 0xff); //length \ i = 2 , 3
        ipv4Packet[i++] = (byte) (length & 0xff);


        ipv4Packet[i++] = 0; //ident \  i = 4 , 5
        ipv4Packet[i++] = 0;

        ipv4Packet[i++] = (1 << 6); //flags i = 6


        ipv4Packet[i++] = 0; //offset i = 7

        ipv4Packet[i++] = 50; //ttl assume every pckt has 50 \ i=8

        ipv4Packet[i++] = 17; //protocol i = 9

        ipv4Packet[12] = (byte) 12;
        ipv4Packet[13] = (byte) 13;
        ipv4Packet[14] = (byte) 14;
        ipv4Packet[15] = (byte) 15;

        ipv4Packet[16] = (byte) 52;     // dest addr 52.37.88.154
        ipv4Packet[17] = (byte) 37;
        ipv4Packet[18] = (byte) 88;
        ipv4Packet[19] = (byte) 154;


        // checksum
        short checkSum = checkSum(ipv4Packet);
        ipv4Packet[10] = (byte) ((checkSum >> 8) & 0xff);
        ipv4Packet[11] = (byte) (checkSum & 0xff);

        // data
        for (i = 0; i < additionalData.length; i++) {
            ipv4Packet[i + MIN_PACKET_SIZE] = additionalData[i];
        }


        return ipv4Packet;
    }

    public short checkSum(byte[] b) {

        Long sum = (long) 0;
        Long temp;
        int i = 0;
        while (i < b.length) {
            temp = (long) b[i++] & 0xFF;
            temp = temp << 8;
            if (i < b.length)
                temp |= b[i++] & 0xFF;
            sum += temp;
            //   System.out.println(Long.toHexString(temp & 0xFFFF).toUpperCase());
            //  System.out.println(Long.toHexString(sum & 0xFFFFFFFF).toUpperCase());
            if ((sum & 0xFFFF0000) > 0) {
                sum &= 0xFFFF;
                sum++;
                // System.out.println("carry occured " + i);
            }
        }
        return (short) ~(sum & 0xFFFF);
    }


    public static void main(String[] args) {
        new UdpClient(SERVER_NAME, PORT_NUMBER);
    }

}
