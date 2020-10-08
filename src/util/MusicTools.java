package util;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import shared.ArtistName;
import shared.MP3Chunk;
import shared.SongInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class MusicTools {

    public static Map<ArtistName, List<SongInfo>> loadSongs() {
        Map<ArtistName, List<SongInfo>> result = new HashMap<>();
        final int chunkSize = 512000;
        try {

            Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);

            List<Path> paths = new ArrayList<>();

            Files.walk(Paths.get("data"))
                    .filter(Files::isRegularFile)
                    .forEach(paths::add);

            for (Path path : paths) {
                String trackName = "";
                String artistName = "";
                String albumInfo = "";
                String genre = "";

                AudioFile f = AudioFileIO.read(new File(path.toString()));
                Tag tag = f.getTag();

                if (tag != null) {
                    artistName = tag.getFirst(FieldKey.ARTIST);
                    albumInfo = tag.getFirst(FieldKey.ALBUM);
                    trackName = tag.getFirst(FieldKey.TITLE);
                    genre = tag.getFirst(FieldKey.GENRE);

                    if (!artistName.isEmpty()) {
                        List<SongInfo> artistSongs = new ArrayList<>();
                        SongInfo songInfo = new SongInfo(
                                trackName,
                                artistName,
                                albumInfo,
                                genre,
                                path.toString(),
                                (int) Math.ceil(f.getFile().length() * 1f / chunkSize)
                        );


                        ArtistName artist = artistExists(artistName, result.keySet());
                        if (artist != null) {
                            artistSongs = result.get(artist);
                        } else {
                            artist = new ArtistName();
                            artist.setArtistName(artistName);
                        }
                        artistSongs.add(songInfo);
                        result.put(artist, artistSongs);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static ArtistName artistExists(String artistName, Set<ArtistName> artistNames) {

        for (ArtistName artist : artistNames) {
            if (artistName.equals(artist.getArtistName())) {
                return artist;
            }
        }
        return null;
    }

    public static Collection<List<ArtistName>> splitList(int numberOfParts, List<ArtistName> artistNames) {

        AtomicInteger counter = new AtomicInteger(0);

        Collection<List<ArtistName>> partitioned = artistNames.stream()
                .collect(Collectors.groupingBy(obj -> counter.getAndIncrement() / ((artistNames.size()) / numberOfParts)))
                .values();

        return partitioned;
    }

    public static ArtistName getArtistNameByString(String artistName, Map<ArtistName, List<SongInfo>> allArtistsMap) {
        Set<ArtistName> artistNames = allArtistsMap.keySet();
        for (ArtistName artist : artistNames) {
            if (artist.getArtistName().equalsIgnoreCase(artistName)) {
                return artist;
            }
        }
        return null;
    }

    public static List<MP3Chunk> splitSongToChunks(SongInfo song, int chunkSize) {
        List<MP3Chunk> chunks = new ArrayList<>();
        try {
            int partNo = 0;
            byte[] bytes = Files.readAllBytes(Path.of(song.getSongPath()));
            for (int i = 0; i < bytes.length; i += chunkSize) {
                int to = i + chunkSize;
                if (to > bytes.length) {
                    to = bytes.length;
                }
                byte[] musicFileExtract = Arrays.copyOfRange(bytes, i, to);
                MP3Chunk chunk = new MP3Chunk(song, partNo, musicFileExtract);
                chunks.add(chunk);
                partNo++;

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return chunks;
    }

    public static List<SongInfo> getSongsOfArtist(ArtistName artist, Map<ArtistName, List<SongInfo>> songsDB) {
//        System.out.println("Music Tools : DB is " + songsDB);
//        System.out.println("Songs of " + artist + " are " + songsDB.get(artist));
        return songsDB.get(artist);
    }
}
