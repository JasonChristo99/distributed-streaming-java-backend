package consumer;

import shared.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.Map.Entry;


public class Consumer {

    private Map<NodeInfo, List<ArtistName>> brokerData;
    private List<NodeInfo> brokers;
    public static String knownBrokerIp = "localhost";
    public static int knownBrokerPort = 6000;


    static Consumer thisConsumer;

    public static void main(String[] args) {
        thisConsumer = new Consumer();
        thisConsumer.registerToBroker();
        //get the localhost IP address, if server is running on some other IP, you need to use that
        try {

            while (true) {

                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                System.out.println("Please Enter an Artist name :");
                Map<NodeInfo, ArtistName> brokerArtistNameMap;

                // user first picks an artist
                do {
                    String requestedArtist = br.readLine();
                    brokerArtistNameMap = thisConsumer.findCorrectBrokerByArtistName(requestedArtist);
                    if (brokerArtistNameMap == null) {
                        System.out.println("The requested artist wasn't found in the database");
                        System.out.println("Please enter another Artist name :");
                    }
                } while (brokerArtistNameMap == null);


                NodeInfo broker = brokerArtistNameMap.keySet().iterator().next();
                ArtistName artist = brokerArtistNameMap.get(broker);
                List<SongInfo> songInfoList = thisConsumer.requestArtist(broker, artist);
                if (songInfoList != null && !songInfoList.isEmpty()) {
                    System.out.println("The artist's songs are : ");
                    int cnt = 1;
                    for (SongInfo songInfo : songInfoList) {
                        System.out.println(cnt + ". " + songInfo);
                        cnt++;
                    }
                } else {
                    System.out.println("Error when getting the songs of the artist");
                    continue;
                }

                // user then picks a song of the artist
                int songIndex;
                do {
                    System.out.println("Please Pick a Song :");
                    String requestedSong = br.readLine();
                    songIndex = Integer.parseInt(requestedSong);
                } while (songIndex < 1 || songIndex > songInfoList.size());

                SongInfo songInfo = songInfoList.get(songIndex - 1);

                // request chunks
                System.out.println("Ask broker: " + broker.getIp() + ":" + broker.getPort() + " for " + songInfo.getSongTitle());
                for (int i = 0; i < songInfo.getPartsTotal(); i++) {
                    ChunkRequest chunkRequest = new ChunkRequest(songInfo, i);
                    MP3Chunk mp3Chunk = (MP3Chunk) thisConsumer.sendDataToServer(broker.getIp(), broker.getPort(), chunkRequest);
                    System.out.println("Got chunk of requested song : " + mp3Chunk);
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    public void registerToBroker() {
        boolean dataReceived = true;
        while (dataReceived) {
            System.out.println("Trying to connect to server...");
            setBrokerData((Map<NodeInfo, List<ArtistName>>) sendDataToServer(knownBrokerIp, knownBrokerPort, "Show me the money"));
//            System.out.println("Broker data got from master broker : " + getBrokerData());
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

    public List<SongInfo> requestArtist(NodeInfo broker, ArtistName artistName) {
        while (true) {
            System.out.println("Ask broker: " + broker.getIp() + ":" + broker.getPort() + " for " + artistName.getArtistName());
            List<SongInfo> songInfoList = (List<SongInfo>) sendDataToServer(broker.getIp(), broker.getPort(), artistName);
            if (songInfoList != null)
                return songInfoList;
        }
    }

    public Map<NodeInfo, ArtistName> findCorrectBrokerByArtistName(String artistNameName) {
        Map<NodeInfo, ArtistName> result = new HashMap<>();
        Set<Entry<NodeInfo, List<ArtistName>>> entrySet = getBrokerData().entrySet();
        for (Entry entry : entrySet) {
            for (ArtistName artistName : (List<ArtistName>) entry.getValue()) {
                if (artistNameName.equals(artistName.getArtistName())) {
                    result.put((NodeInfo) entry.getKey(), artistName);
                    return result;
                }
            }
        }
        return null;
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


    public Map<NodeInfo, List<ArtistName>> getBrokerData() {
        return this.brokerData;
    }

    public void setBrokerData(Map<NodeInfo, List<ArtistName>> brokerData) {
        this.brokerData = brokerData;
    }

    public void setBrokers(List<NodeInfo> brokers) {
        this.brokers = brokers;
    }
}
