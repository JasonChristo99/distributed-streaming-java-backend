package broker;

import shared.ArtistName;
import shared.ChunkRequest;
import shared.NodeInfo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Map.Entry;


public class Broker implements Comparable<Broker> {

    private final String[] brokerIps = {"localhost", "localhost", "localhost"};
    public final int[] brokerPorts = {6000, 6001, 6002};
    private Map<NodeInfo, List<ArtistName>> publisherData;
    private Map<NodeInfo, List<ArtistName>> brokerData;
    private int brokerNo = -1;
    private List<NodeInfo> brokers = new ArrayList<>();
    private static int modInt = 19;

    static Broker thisBroker;

    public static void main(String[] arg) {
        if (arg.length != 1) return;
        int thisBrokerNo = Integer.parseInt(arg[0]);

        thisBroker = new Broker(thisBrokerNo);
        thisBroker.init();

        System.out.println("This broker is : " + thisBroker);
        thisBroker.startServer(thisBroker.getPort());
    }


    public Broker(int brokerNo) {
        this.brokerNo = brokerNo;
    }

    public void init() {
        for (int i = 0; i < brokerIps.length; i++) {
            NodeInfo broker = new NodeInfo(brokerIps[i], brokerPorts[i], i);
            brokers.add(broker);
        }
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


                System.out.println("Read data : " + inputObject);
                response = processData(inputObject);

                output.writeObject(response);
                System.out.println("Responded with : " + response);

                output.flush();
                clientSocket.close();
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException: " + serverPort);
        }

    }


    public Object processData(Object data) {
        Object result = new Object();
        if (data instanceof Map) {
            result = processDataFromPublisher((Map<NodeInfo, List<ArtistName>>) data);
            if (brokerData == null || brokerData.isEmpty())
                setBrokerData((Map<NodeInfo, List<ArtistName>>) result);
            if (this.publisherData == null) {
                setPublisherData((Map<NodeInfo, List<ArtistName>>) data);
                System.out.println("Publisher data set to " + publisherData);
            }
        } else if (data instanceof ArtistName) {
            result = processArtistDataFromConsumer((ArtistName) data);
        } else if (data instanceof ChunkRequest) {
            result = processChunkRequestFromConsumer((ChunkRequest) data);
        } else if (data instanceof String) {
            if ("Show me the money".equals(data)) {
                result = brokerData;
            }
        }
//        System.out.println("Broker " + this + " is processing data...");
//        System.out.println("Result after processing is : " + result);
        return result;
    }

    private Object processChunkRequestFromConsumer(ChunkRequest chunkRequest) {
        Map<NodeInfo, ArtistName> publisherArtistNameMap = findCorrectPublisherByArtistName(chunkRequest.getSongInfo().getArtistName());
        if (publisherArtistNameMap != null) {
            NodeInfo publisher = publisherArtistNameMap.keySet().iterator().next();
            return sendDataToServer(publisher.getIp(), publisher.getPort(), chunkRequest);
        }

        return null;
    }

    private Object processArtistDataFromConsumer(ArtistName artistName) {
        Map<NodeInfo, ArtistName> publisherArtistNameMap = findCorrectPublisherByArtistName(artistName.getArtistName());
        if (publisherArtistNameMap != null) {
            NodeInfo publisher = publisherArtistNameMap.keySet().iterator().next();
            ArtistName artist = publisherArtistNameMap.get(publisher);
            return sendDataToServer(publisher.getIp(), publisher.getPort(), artist);
        }

        return null;
    }

    private Object processDataFromPublisher(Map<NodeInfo, List<ArtistName>> data) {
        List<ArtistName> listOfArtists = new ArrayList<>();
        List<List<ArtistName>> artists = new ArrayList<>(data.values());
        for (List<ArtistName> list : artists) {
            listOfArtists.addAll(list);
        }
        return calculateKeys(listOfArtists);
    }


    public Map<NodeInfo, List<ArtistName>> calculateKeys(List<ArtistName> listOfArtistNames) {
        Map<NodeInfo, List<ArtistName>> responsibleFor = new HashMap<>();
        Map<BigInteger, NodeInfo> brokerHashesMap = new HashMap<>();
        Map<BigInteger, ArtistName> artistNameHashesMap = new HashMap<>();
//        System.out.println("Broker: Brokers are " + getBrokers());
        for (NodeInfo broker : getBrokers()) {
            BigInteger hash = hashCode(broker.getIp() + ":" + broker.getPort()).mod(BigInteger.valueOf(modInt));
            if (hash != null) {
                brokerHashesMap.put(hash, broker);
            }
            responsibleFor.put(broker, new ArrayList<>());
        }

//        System.out.println("Broker hashes size: " + brokerHashesMap.size());
//        System.out.println("Broker hashes : " + brokerHashesMap);

        for (ArtistName artistName : listOfArtistNames) {
            BigInteger hash = hashCode(artistName.getArtistName()).mod(BigInteger.valueOf(modInt));
            if (hash != null) {
                artistNameHashesMap.put(hash, artistName);
            }
        }

//        System.out.println("Artist name hashes size: " + artistNameHashesMap.size());
//        System.out.println("Artist name hashes : " + artistNameHashesMap);

        List<BigInteger> brokerHashes = new ArrayList<>(brokerHashesMap.keySet());
        List<BigInteger> artistNameHashes = new ArrayList<>(artistNameHashesMap.keySet());
        Collections.sort(brokerHashes);

        for (BigInteger artistNameHash : artistNameHashes) {
            BigInteger tmpHash = findRightBroker(brokerHashes, artistNameHash);
//            System.out.println("Broker " + tmpHash + " responsible for artist " + artistNameHash);
            if (tmpHash != null) {
                responsibleFor.get(brokerHashesMap.get(tmpHash)).add(artistNameHashesMap.get(artistNameHash));
            }
        }

//        System.out.println("Responsible for map : " + responsibleFor);
        return responsibleFor;
    }

    private static BigInteger hashCode(String strBeforeHash) {
        BigInteger bigInt = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] messageDigest = md.digest(strBeforeHash.getBytes());
            md.update(messageDigest);
            byte[] digested = md.digest();
            //Decoding
            bigInt = new BigInteger(1, digested);

        } catch (NoSuchAlgorithmException e) {

        } finally {
            return bigInt;
        }
    }

    private BigInteger findRightBroker(List<BigInteger> brokerHashes, BigInteger artistNameHash) {
        for (int i = 0; i < brokerHashes.size(); i++) {
            if (artistNameHash.compareTo(brokerHashes.get(i)) < 0) {
//                System.out.println("First case <");
                return brokerHashes.get(i);
            } else {
                if (i == brokerHashes.size() - 1) {
//                    System.out.println("Second case mod 1");
//                    BigInteger afterMod = artistNameHash.mod(brokerHashes.get(i));
//                    for (int j = 0; j < brokerHashes.size(); j++) {
//                        if (afterMod.compareTo(brokerHashes.get(j)) < 0) {
//                            return brokerHashes.get(j);
//                        }
//                    }
                    return brokerHashes.get(0);
                }
            }
        }
        return null;
    }

    public Map<NodeInfo, ArtistName> findCorrectPublisherByArtistName(String artistNameName) {
        Map<NodeInfo, ArtistName> result = new HashMap<>();
        System.out.println("Broker has publisher data : " + getPublisherData());
        Set<Entry<NodeInfo, List<ArtistName>>> entrySet = getPublisherData().entrySet();
        for (Entry entry : entrySet) {
            for (ArtistName artistName : (List<ArtistName>) entry.getValue()) {
                if (artistNameName.equals(artistName.getArtistName())) {
                    result.put((NodeInfo) entry.getKey(), artistName);
                    System.out.println("Correct publisher : " + result);
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

    public String getIp() {
        return brokerIps[brokerNo];
    }

    public int getPort() {
        return brokerPorts[brokerNo];
    }

    public Map<NodeInfo, List<ArtistName>> getPublisherData() {
        return publisherData;
    }

    public void setPublisherData(Map<NodeInfo, List<ArtistName>> publisherData) {
        this.publisherData = publisherData;
        if (brokerNo != 0) return;
        for (NodeInfo broker : getBrokers()) {
            if (broker.getNo() == this.brokerNo) continue;
//            System.out.println("Setting publisher data for broker : " + broker);
            Object response = sendDataToServer(broker.getIp(), broker.getPort(), publisherData);
//            System.out.println("Response on broker set publisher data " + response);
        }
    }

    public List<NodeInfo> getBrokers() {
        return brokers;
    }

    public Map<NodeInfo, List<ArtistName>> getBrokerData() {
        return brokerData;
    }

    public void setBrokerData(Map<NodeInfo, List<ArtistName>> brokerData) {
        this.brokerData = brokerData;
    }

    @Override
    public String toString() {
        return "Broker{" +
                "no=" + brokerNo +
                ", " + getIp() +
                ":" + getPort() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Broker broker = (Broker) o;

        return brokerNo == broker.brokerNo;
    }

    @Override
    public int hashCode() {
        return "broker".hashCode() + brokerNo;
    }

    @Override
    public int compareTo(Broker o) {
        return Integer.compare(brokerNo, o.brokerNo);
    }
}

