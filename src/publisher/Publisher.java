package publisher;

import shared.ArtistName;
import shared.ChunkRequest;
import shared.NodeInfo;
import shared.SongInfo;
import util.MusicTools;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.*;


public class Publisher {

    private Map<NodeInfo, List<ArtistName>> brokerData;
    private final String[] publisherIps = {"localhost", "localhost"};
    public final int[] publisherPorts = {7000, 7001};
    private int publisherNo = -1;
    private List<ArtistName> allArtists = new ArrayList<>();
    private List<NodeInfo> publishers = new ArrayList<>();
    private List<NodeInfo> brokers;
    public static String knownBrokerIp = "localhost";
    public static int knownBrokerPort = 6000;

    public static int CHUNK_SIZE = 512000;


    static Publisher thisPublisher;

    public static void main(String[] arg) {
        if (arg.length != 1) return;
        int thisPublisherNo = Integer.parseInt(arg[0]);

        thisPublisher = new Publisher(thisPublisherNo);
        thisPublisher.init();

        thisPublisher.connectToBroker();

        thisPublisher.startServer(thisPublisher.getPort());
    }


    public Publisher(int publisherNo) {
        this.publisherNo = publisherNo;
    }

    public void init() {
        for (int i = 0; i < publisherIps.length; i++) {
            NodeInfo publisher = new NodeInfo(publisherIps[i], publisherPorts[i], i);
            publishers.add(publisher);
        }
    }


    public Map<NodeInfo, List<ArtistName>> createAllPublisherData(List<NodeInfo> publishers) {
        Map<NodeInfo, List<ArtistName>> data = new HashMap<>();
        Map<ArtistName, List<SongInfo>> songData = MusicTools.loadSongs();

        Set<ArtistName> artistNameSet = songData.keySet();
        for (ArtistName artistName : artistNameSet) {
            getAllArtists().add(artistName);
        }

        Collections.sort(getAllArtists());
//        System.out.println("All artists: " + getAllArtists() + " of total : " + getAllArtists().size());
//        System.out.println("Publishers total: " + publishers.size());
        List<List<ArtistName>> parts = new ArrayList<>(MusicTools.splitList(publishers.size(), getAllArtists()));
        for (int i = 0; i < publishers.size(); i++) {
            data.put(publishers.get(i), parts.get(i));
        }
//        System.out.println("Partitioned publisher data map : " + data);
        return data;
    }

    public void startServer(int serverPort) {
        ServerSocket serverSocket;
        Socket clientSocket;
        Object response;
        try {
            serverSocket = new ServerSocket(serverPort);
            while (true) {
                System.out.println("Server on port " + serverPort + " is waiting for a client.");
                clientSocket = serverSocket.accept();
                ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());

                Object inputObject = input.readObject();
                response = processData(inputObject);

                System.out.println("Processed some data " + response);
                output.writeObject(response);
                System.out.println("Written to output stream : " + response);
                output.flush();
                clientSocket.close();
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException: " + serverPort);
        }

    }

    public Object processData(Object data) {
//        System.out.println("Publisher is processing some data...");
        Object result = new Object();

        if (data instanceof ArtistName) {
            result = processArtistDataFromBroker(((ArtistName) data));
        } else if (data instanceof ChunkRequest) {
            result = processChunkRequestFromBroker(((ChunkRequest) data));
        }
//        System.out.println("Publisher processed some data " + data + " with result: " + result);
        return result;
    }

    private Object processArtistDataFromBroker(ArtistName artistName) {
        Map<ArtistName, List<SongInfo>> songData = MusicTools.loadSongs();
        return MusicTools.getSongsOfArtist(artistName, songData);
    }

    private Object processChunkRequestFromBroker(ChunkRequest chunkRequest) {
        return MusicTools.splitSongToChunks(chunkRequest.getSongInfo(), CHUNK_SIZE).get(chunkRequest.getPartNo());
    }


    void connectToBroker() {
        Map<NodeInfo, List<ArtistName>> data = createAllPublisherData(getPublishers());
        System.out.println("All data : " + data + " of total " + data.size());
        System.out.println("Data of publisher " + getPublisherNo() + " : " + data.get(this));

        boolean dataReceived = true;
        while (dataReceived) {
            System.out.println("Trying to connect to server...");
            Map<NodeInfo, List<ArtistName>> brokerData = (Map<NodeInfo, List<ArtistName>>)
                    sendDataToServer(knownBrokerIp, knownBrokerPort, data);
            setBrokerData(brokerData);
            if (getBrokerData().size() > 0) {
                System.out.println(getBrokerData().size());
                List<NodeInfo> brokers = new ArrayList<>();
                for (NodeInfo broker : getBrokerData().keySet()) {
                    brokers.add(broker);
                }
                setBrokers(brokers);
                dataReceived = false;
            }
        }

    }


    public Object sendDataToServer(String hostName, int port, Object data) {

        SocketAddress socketAddress = new InetSocketAddress(hostName, port);
        Socket socket = new Socket();
        Object response = new Object();
        try {
            socket.connect(socketAddress, 2000);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            System.out.println("Server wrote output data : " + data);
            out.writeObject(data);
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            response = in.readObject();
            System.out.println("Server read input data : " + response);
            out.close();
            in.close();
            socket.close();

        } catch (SocketTimeoutException exception) {
            System.out.println("SocketTimeoutException " + hostName + ":" + port + ". " + exception.getMessage());
        } catch (IOException exception) {
            System.out.println(
                    "IOException - Unable to connect to " + hostName + ":" + port + ". " + exception.getMessage());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return response;

    }


    public String getIp() {
        return publisherIps[publisherNo];
    }

    public int getPort() {
        return publisherPorts[publisherNo];
    }

    public int getPublisherNo() {
        return publisherNo;
    }

    public List<ArtistName> getAllArtists() {
        return allArtists;
    }

    public List<NodeInfo> getPublishers() {
        return publishers;
    }

    public Map<NodeInfo, List<ArtistName>> getBrokerData() {
        return brokerData;
    }

    public void setBrokerData(Map<NodeInfo, List<ArtistName>> brokerData) {
        this.brokerData = brokerData;
    }

    public void setBrokers(List<NodeInfo> brokers) {
        this.brokers = brokers;
    }

    @Override
    public String toString() {
        return "Publisher{" +
                "no=" + publisherNo +
                ", " + getIp() +
                ":" + getPort() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Publisher publisher = (Publisher) o;

        return publisherNo == publisher.publisherNo;
    }

    @Override
    public int hashCode() {
        return "publisher".hashCode() + publisherNo;
    }
}
