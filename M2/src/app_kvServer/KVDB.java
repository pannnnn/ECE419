package app_kvServer;


import ecs.IECSNode;
import org.apache.log4j.Logger;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Math.abs;


public class KVDB {

    private Logger logger = Logger.getRootLogger();

    ArrayList<ReentrantReadWriteLock> lockList;

    // constant fileSize, # of blocks in a file
    private static final long fileBlock = 5000;


    /**
     * one block contains:
     * 1 byte for occupied flag, 1 byte for grey flag;
     * 4 bytes for key size, 4 bytes for value size;
     * 20 bytes for key, 120000 bytes for value;
     */
    private static final long blockSize = 1 + 1 + 4 + 4 + 20 + 120000;

    private int fileNumber;

    private String name;

    private String file_path;

    public KVDB(String name) throws IOException {

        this.name = name;
        file_path = "KVDB/" + this.name + "/db";

        initializeDB();
    }


    private void initializeDB() throws IOException {

        lockList = new ArrayList<>();

        File root = new File("KVDB");
        if (!root.exists()) root.mkdir();

        File DB = new File("KVDB/" + name);

        if (!DB.exists() || DB.list().length == 0) {

            DB.mkdir();
            //Create two files into DB directory
            for (int i = 0; i < 2; i++) {
                //Create a file with name DB#
                File DBfile = new File(file_path + String.valueOf(i));
                ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

                DBfile.createNewFile();

                //Create RandomAccessFile associate with the file
                RandomAccessFile file = new RandomAccessFile(DBfile, "rw");

                //Set the file length to blockSize * fileSize
                file.setLength(blockSize * fileBlock);

                //close file
                file.close();

                //Add lock to the locks list
                lockList.add(lock);
            }

            fileNumber = 2;

        } else {

            for (int i = 0; i < DB.list().length; i++) {

                ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
                lockList.add(lock);
            }
            fileNumber = DB.list().length;

        }

    }


    public void clear() throws IOException {

        logger.info("Delete directory... ");


        for (int i = 0; i < fileNumber; i++) {
            lockList.get(i).writeLock().lock();

            File DBfile = new File(file_path + String.valueOf(i));
            DBfile.delete();

            lockList.get(i).writeLock().unlock();
            ;
        }

        File DB = new File("KVDB/" + name);
        DB.delete();

        logger.info("Done Delete directory... ");
        initializeDB();
    }


    public void putKV(String K, String V) throws IOException {

        if (updateKV(K, V)) return;

        long blockIndex = getHash(K);

        long count = NumberOfTotalBlock();
        while (count > 0) {


            //check if current block is occupied
            if (!checkBlock(blockIndex, true)) {
                writeKeyValueAtBlock(blockIndex, K, V);
                return;
            }

            if (blockIndex == NumberOfTotalBlock() - 1) blockIndex = 0;
            else blockIndex++;

            count--;
        }
        // do not have enough space
        // need to extend the files
        // TODO need to extend the file system
        return;
    }

    private boolean updateKV(String K, String V) throws IOException {

        long blockIndex = getHash(K);

        long count = NumberOfTotalBlock();
        while (count > 0) {

            //check if current block is right
            if (getKeyAtBlock(blockIndex).equals(K)) {
                writeKeyValueAtBlock(blockIndex, K, V);
                return true;
            }

            //check if current block is clean
            if (!checkBlock(blockIndex, false)) {
                return false;
            }

            if (blockIndex == NumberOfTotalBlock() - 1) blockIndex = 0;
            else blockIndex++;

            count--;
        }
        // did not find the key
        return false;
    }


    public String getKV(String K) throws IOException {

        long blockIndex = getHash(K);

        long count = NumberOfTotalBlock();
        while (count > 0) {

            if (getKeyAtBlock(blockIndex).equals(K)) {
                return getValueAtBlock(blockIndex);
            }

            //check if current block is clean
            if (!checkBlock(blockIndex, false)) {
                return null;
            }

            if (blockIndex == NumberOfTotalBlock() - 1) blockIndex = 0;
            else blockIndex++;

            count--;
        }
        // do not have enough space
        return null;
    }

    public HashMap<String, String> getRangeKV(String[] range) throws IOException {

        String from = range[0];
        String to = range[1];

        boolean edge = from.compareTo(to) >= 0;

        HashMap<String, String> map = new HashMap<String, String>();

        long count = NumberOfTotalBlock() - 1;
        while (count >= 0) {

            String key = getKeyAtBlock(count);

            if (!key.equals("")) {


                MessageDigest md;
                String keyHashValue = null;
                try {
                    md = MessageDigest.getInstance("MD5");
                    md.update(key.getBytes());
                    byte[] digest = md.digest();
                    keyHashValue = DatatypeConverter.printHexBinary(digest).toUpperCase();
                } catch (NoSuchAlgorithmException e) {
                    logger.error("Cannot transfer hash " + e.getMessage());
                }

                if (edge) {
                    if (keyHashValue.compareTo(from) >= 0 || keyHashValue.compareTo(to) < 0) {
                        map.put(key, getValueAtBlock(count));
                    }
                } else {
                    if (keyHashValue.compareTo(from) >= 0 && keyHashValue.compareTo(to) < 0) {
                        map.put(key, getValueAtBlock(count));
                    }
                }
            }
            count--;
        }


        return map;
    }


    public void removeRangeKV(String[] range) throws IOException {

        String from = range[0];
        String to = range[0];

        boolean edge = from.compareTo(to) > 0;


        long count = NumberOfTotalBlock() - 1;
        while (count >= 0) {

            String key = getKeyAtBlock(count);
            if (!key.equals("")) {

                MessageDigest md;
                String keyHashValue = null;
                try {
                    md = MessageDigest.getInstance("MD5");
                    md.update(key.getBytes());
                    byte[] digest = md.digest();
                    keyHashValue = DatatypeConverter.printHexBinary(digest).toUpperCase();
                } catch (NoSuchAlgorithmException e) {
                    logger.error("Cannot transfer hash " + e.getMessage());
                }

                if (edge) {
                    if (keyHashValue.compareTo(from) >= 0 || keyHashValue.compareTo(to) < 0) {
                        writeKeyValueAtBlock(count, key, "");
                    }
                } else {
                    if (keyHashValue.compareTo(from) >= 0 && keyHashValue.compareTo(to) < 0) {
                        writeKeyValueAtBlock(count, key, "");
                    }
                }
            }
            count--;
        }
    }


    public boolean extend() throws IOException {

        return false;

    }

    // location take the hash location overall all files
    // type true => read first byte, false => read second byte
    private boolean checkBlock(long blockIndex, boolean type) throws IOException {

        int i = (int) (blockIndex / fileBlock);
        File DBfile = new File(file_path + String.valueOf(i));
        RandomAccessFile file = new RandomAccessFile(DBfile, "r");

        lockList.get(i).readLock().lock();

        long location = (blockIndex % fileBlock) * blockSize;
        file.seek(location + (type ? 0 : 1));
        boolean byte_bool = file.readBoolean();

        lockList.get(i).readLock().unlock();

        file.close();

        return byte_bool;
    }

    //get the key at given block
    private String getKeyAtBlock(long blockIndex) throws IOException {
        int i = (int) (blockIndex / fileBlock);
        File DBfile = new File(file_path + String.valueOf(i));
        RandomAccessFile file = new RandomAccessFile(DBfile, "r");

        lockList.get(i).readLock().lock();

        long location = (blockIndex % fileBlock) * blockSize;

        //read the size of key
        file.seek(location);
        if (!file.readBoolean()) {
            lockList.get(i).readLock().unlock();
            file.close();
            return "";
        }

        //read the size of key
        file.seek(location + 2);
        int keySize = file.readInt();

        //read the key
        file.seek(location + 2 + 8);
        byte[] key = new byte[keySize];
        file.readFully(key);

        lockList.get(i).readLock().unlock();
        file.close();

        return new String(key);
    }

    //get the key at given block
    private String getValueAtBlock(long blockIndex) throws IOException {
        int i = (int) (blockIndex / fileBlock);
        File DBfile = new File(file_path + String.valueOf(i));
        RandomAccessFile file = new RandomAccessFile(DBfile, "r");

        lockList.get(i).readLock().lock();

        long location = (blockIndex % fileBlock) * blockSize;

        //read the size of key
        file.seek(location + 6);
        int valueSize = file.readInt();

        //read the key
        file.seek(location + 2 + 8 + 20);
        byte[] value = new byte[valueSize];
        file.readFully(value);

        lockList.get(i).readLock().unlock();
        file.close();

        return new String(value);
    }

    // write value at given block
    private void writeKeyValueAtBlock(long blockIndex, String K, String V) throws IOException {

        int i = (int) (blockIndex / fileBlock);
        File DBfile = new File(file_path + String.valueOf(i));
        RandomAccessFile file = new RandomAccessFile(DBfile, "rw");

        lockList.get(i).writeLock().lock();

        long location = (blockIndex % fileBlock) * blockSize;

        //going to the block location
        file.seek(location);

        if (V == null || V.equals("")) {
            file.writeBoolean(false);
            lockList.get(i).writeLock().unlock();
            file.close();
            return;
        }

        //start writing
        //occupied
        file.writeBoolean(true);
        //grey
        file.writeBoolean(true);
        //size of key
        file.writeInt(K.length());
        //size of value
        file.writeInt(V.length());
        //key
        file.writeBytes(K);
        file.seek(location + blockSize - 120000);
        //value
        file.writeBytes(V);

        lockList.get(i).writeLock().unlock();
        file.close();

    }

    private long NumberOfTotalBlock() {
        return (fileNumber * fileBlock);
    }


    private long getHash(String K) {
        return abs(K.hashCode()) % NumberOfTotalBlock();
    }
}
