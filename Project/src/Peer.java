import java.io.*;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Peer implements RMIRemote {

    private static int id;
    private static ChannelControl MC;
    private static ChannelBackup MDB;
    private static ChannelRestore MDR;
    private static ScheduledThreadPoolExecutor exec;
    private static Storage storage;

    public Peer() {
        exec = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(50);
        MC = new ChannelControl();
        MDB = new ChannelBackup();
        MDR = new ChannelRestore();
        storage = new Storage();
    }

    public static int getId() {
        return id;
    }

    public static ScheduledThreadPoolExecutor getExec() {
        return exec;
    }

    public static ChannelControl getMC() {
        return MC;
    }

    public static ChannelBackup getMDB() {
        return MDB;
    }

    public static ChannelRestore getMDR() {
        return MDR;
    }

    public static Storage getStorage() {
        return storage;
    }

    public static void main(String args[]) {

        System.setProperty("java.net.preferIPv4Stack", "true");

        try {
            Peer obj = new Peer();
            obj.id = Integer.parseInt(args[0]);
            RMIRemote stub = (RMIRemote) UnicastRemoteObject.exportObject(obj, 0);

            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.getRegistry();
            registry.bind(args[0], stub);

            System.err.println("Peer ready");
        } catch (Exception e) {
            System.err.println("Peer exception: " + e.toString());
            e.printStackTrace();
        }

        exec.execute(MC);
        exec.execute(MDB);
        exec.execute(MDR);
    }


    public synchronized void backup(String filepath, int replicationDegree) {

        FileData file = new FileData(filepath, replicationDegree);
        storage.addFile(file);

        for (int i = 0; i < file.getChunks().size(); i++) {
            Chunk chunk = file.getChunks().get(i);
            chunk.setDesiredReplicationDegree(replicationDegree);

            String header = "PUTCHUNK " + "1.0" + " " + id + " " + file.getId() + " " + chunk.getNr() + " " + chunk.getDesiredReplicationDegree() + "\r\n\r\n";
            System.out.println("Sented PUTCHUNK chunk size: " + chunk.getSize());

            String key = file.getId() + "_" + chunk.getNr();
            if (!storage.getStoredOccurrences().containsKey(key)) {
                Peer.getStorage().getStoredOccurrences().put(key, 0);
            }

            try {
                byte[] asciiHeader = header.getBytes("US-ASCII");
                byte[] body = chunk.getContent();
                byte[] message = new byte[asciiHeader.length + body.length];
                System.arraycopy(asciiHeader, 0, message, 0, asciiHeader.length);
                System.arraycopy(body, 0, message, asciiHeader.length, body.length);

                SendMessageThread sendThread = new SendMessageThread(message, "MDB");
                exec.execute(sendThread);

                Peer.getExec().schedule(new PutChunkManager(message, 1, file.getId(), chunk.getNr(), replicationDegree), 1, TimeUnit.SECONDS);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    public void restore(String filepath) {

    }

    public void delete(String filepath) {

        for (int i = 0; i < storage.getFiles().size(); i++) {
            if (storage.getFiles().get(i).getFile().getPath().equals(filepath)) {

                String header = "DELETE " + "1.0" + " " + id + " " + storage.getFiles().get(i).getId() + "\r\n\r\n";

                try {
                    SendMessageThread sendThread = new SendMessageThread(header.getBytes("US-ASCII"), "MDB");

                    exec.execute(sendThread);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

            } else return;
        }
    }

    public void reclaim(int diskSpaceToReclaim) {

        storage.fillCurrRDChunks();
        storage.getChunks().sort(Collections.reverseOrder());

        ArrayList<Integer> indexsToDelete = new ArrayList<>();

        int total= 0;
        for(int i = 0; i < storage.getChunks().size(); i++){
            if(total< diskSpaceToReclaim){
                indexsToDelete.add(i);
                total = total + storage.getChunks().get(i).getSize();
            }
            else{
                break;
            }
        }

        for (int j = 0; j < indexsToDelete.size(); j++){
            Chunk chunk = storage.getChunks().get(j);

                String header = "REMOVED " + "1.0" + " " + id + " " + chunk.getFileID() + " " + chunk.getNr()  + "\r\n\r\n";
                System.out.println("Sent REMOVED "+ chunk.getFileID() +" " + chunk.getNr() +" size: " + chunk.getSize() + " RD: " + chunk.getCurrReplicationDegree());
                try {
                    byte[] asciiHeader = header.getBytes("US-ASCII");
                    SendMessageThread sendThread = new SendMessageThread(asciiHeader, "MC");
                    exec.execute(sendThread);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            storage.getChunks().remove(j);

            String filename = Peer.getId() + "/" + chunk.getOwner() + "_" + chunk.getFileID() + "_" + chunk.getNr();
            File file = new File(filename);
            file.delete();
        }

    }

    public void state() {
        //Each file whose backup it has initiated
        System.out.println("\n> For each file whose backup it has initiated!");
        for (int i = 0; i < storage.getFiles().size(); i++) {
            String fileID = storage.getFiles().get(i).getId();

            System.out.println("FILE PATHNAME: " + storage.getFiles().get(i).getFile().getPath());
            System.out.println("FILE ID: " + fileID);
            System.out.println("FILE REPLICATION DEGREE: " + storage.getFiles().get(i).getReplicationDegree() + "\n");

            for (int j = 0; j < storage.getFiles().get(i).getChunks().size(); j++) {
                int chunkNr = storage.getFiles().get(i).getChunks().get(j).getNr();
                String key = fileID + '_' + chunkNr;

                System.out.println("CHUNK ID: " + chunkNr);
                System.out.println("CHUNK PERCEIVED REPLICATION DEGREE: " + storage.getStoredOccurrences().get(key) + "\n");
            }
        }

        //Each chunk it stores
        System.out.println("\n> For each chunk it stores!");
        for (int i = 0; i < storage.getChunks().size(); i++) {
            int chunkNr = storage.getChunks().get(i).getNr();
            String key = storage.getChunks().get(i).getFileID() + '_' + chunkNr;
            System.out.println("CHUNK ID: " + chunkNr);
            System.out.println("CHUNK PERCEIVED REPLICATION DEGREE: " + storage.getStoredOccurrences().get(key) + "\n");
        }

    }
}