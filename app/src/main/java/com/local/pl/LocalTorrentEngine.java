package com.local.pl;

import android.content.Context;
import android.os.StatFs;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TorrentFlags;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * An embedded BitTorrent engine and loopback-only HTTP media source.
 * No Jackett, NAS, TorrServer, or publicly reachable listening HTTP port.
 *
 * This is an experimental streamer: piece deadlines are requested for the
 * playback position and the HTTP thread waits until the piece is verified.
 */
public final class LocalTorrentEngine {
    public static final class VideoFile {
        public final int index;
        public final String name;
        public final long length;
        public VideoFile(int i,String n,long l){index=i;name=n;length=l;}
    }
    private static LocalTorrentEngine singleton;
    public static synchronized LocalTorrentEngine get(Context context){
        if(singleton==null)singleton=new LocalTorrentEngine(context.getApplicationContext());
        return singleton;
    }

    private final Context app;
    private final File directory;
    private final SessionManager session=new SessionManager();
    private final ExecutorService clients=Executors.newFixedThreadPool(4);
    private ServerSocket server;
    private volatile PlayItem current;
    private volatile boolean running;
    private final String token=UUID.randomUUID().toString();

    private static final class PlayItem {
        final TorrentInfo info;
        final TorrentHandle handle;
        final File file;
        final int fileIndex;
        final long length,offset;
        PlayItem(TorrentInfo ti,TorrentHandle th,File f,int idx,long len,long off){
            info=ti;handle=th;file=f;fileIndex=idx;length=len;offset=off;
        }
    }

    private LocalTorrentEngine(Context context){
        app=context;
        File root=context.getExternalFilesDir(null);
        if(root==null)root=context.getFilesDir();
        directory=new File(root,"PLCache");
        if(!directory.isDirectory()&&!directory.mkdirs())
            throw new IllegalStateException("Невозможно создать каталог кэша");
    }
    public List<VideoFile> videos(byte[] data){
        TorrentInfo info=new TorrentInfo(data);
        if(!info.isValid())throw new IllegalArgumentException("Повреждённый торрент");
        FileStorage files=info.files();
        ArrayList<VideoFile> found=new ArrayList<>();
        for(int i=0;i<files.numFiles();i++){
            String name=files.filePath(i);
            if(name.toLowerCase(Locale.ROOT).matches("(?s).*\\.(mp4|mkv|avi|mov|wmv|webm|mpeg|mpg|ts|m2ts|vob)$"))
                found.add(new VideoFile(i,name,files.fileSize(i)));
        }
        return found;
    }
    public synchronized String play(byte[] data,int index)throws Exception{
        TorrentInfo info=new TorrentInfo(data);
        if(!info.isValid())throw new IOException("Невозможно открыть торрент");
        FileStorage files=info.files();
        if(index<0||index>=files.numFiles())throw new IOException("Неверный индекс видео");
        if(files.fileSize(index)<1)throw new IOException("Пустой видеофайл");
        if(new StatFs(directory.getAbsolutePath()).getAvailableBytes()<128L*1024L*1024L)
            throw new IOException("Мало свободного места на Google TV Streamer (нужно хотя бы 128 МБ)");
        if(!running){
            session.start();
            server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
            running=true;
            Thread accept=new Thread(this::serve,"PL-LoopbackMedia");
            accept.setDaemon(true);accept.start();
        }
        Priority[] priorities=Priority.array(Priority.IGNORE,files.numFiles());
        priorities[index]=Priority.NORMAL;
        session.download(info,directory,null,priorities,null,
            TorrentFlags.SEQUENTIAL_DOWNLOAD.or(TorrentFlags.AUTO_MANAGED));
        TorrentHandle handle=null;
        for(int tries=0;tries<120;tries++){
            handle=session.find(info);
            if(handle!=null && handle.isValid())break;
            Thread.sleep(150);
        }
        if(handle==null||!handle.isValid())throw new IOException("Торрент-движок не запустил загрузку");
        handle.prioritizeFiles(priorities);
        File f=new File(files.filePath(index,directory.getAbsolutePath())).getCanonicalFile();
        File allowed=directory.getCanonicalFile();
        if(!f.getPath().startsWith(allowed.getPath()+File.separator))
            throw new IOException("Недопустимый путь внутри торрента");
        current=new PlayItem(info,handle,f,index,files.fileSize(index),files.fileOffset(index));
        return "http://127.0.0.1:"+server.getLocalPort()+"/play?token="+token;
    }
    private void serve(){
        while(running){
            try{
                Socket socket=server.accept();
                socket.setSoTimeout(125000);
                clients.submit(()->respond(socket));
            }catch(Exception ignored){
                if(!running)break;
            }
        }
    }
    private void respond(Socket socket){
        try(Socket s=socket){
            BufferedReader reader=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII));
            String request=reader.readLine();
            if(request==null)return;
            boolean head=request.startsWith("HEAD ");
            if(!head&&!request.startsWith("GET ")){error(s,405,"Method Not Allowed");return;}
            if(!request.contains("/play?token="+token)){error(s,403,"Forbidden");return;}
            PlayItem item=current;
            if(item==null){error(s,404,"Not Found");return;}
            long first=0,last=item.length-1;
            String line;
            boolean ranged=false;
            while((line=reader.readLine())!=null && !line.isEmpty()){
                if(line.toLowerCase(Locale.ROOT).startsWith("range: bytes=")){
                    String spec=line.substring(line.indexOf('=')+1).trim();
                    if(spec.contains(",")){error(s,416,"Invalid Range");return;}
                    String[] ends=spec.split("-",2);
                    try{
                        if(ends.length<1||ends[0].isEmpty()){error(s,416,"Invalid Range");return;}
                        first=Long.parseLong(ends[0]);
                        if(ends.length>1&&!ends[1].isEmpty())last=Math.min(last,Long.parseLong(ends[1]));
                        ranged=true;
                    }catch(NumberFormatException e){error(s,416,"Invalid Range");return;}
                }
            }
            if(first<0||first>=item.length||last<first){
                error(s,416,"Range Not Satisfiable");return;
            }
            long len=last-first+1;
            String ext=item.file.getName().toLowerCase(Locale.ROOT);
            String mime=ext.endsWith(".mp4")||ext.endsWith(".m4v")?"video/mp4":
                    ext.endsWith(".mkv")?"video/x-matroska":"application/octet-stream";
            OutputStream out=s.getOutputStream();
            String headers=(ranged?"HTTP/1.1 206 Partial Content\r\n":"HTTP/1.1 200 OK\r\n")
                +"Content-Type: "+mime+"\r\n"
                +"Accept-Ranges: bytes\r\n"
                +"Content-Length: "+len+"\r\n"
                +(ranged?"Content-Range: bytes "+first+"-"+last+"/"+item.length+"\r\n":"")
                +"Cache-Control: no-store\r\nConnection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            if(head)return;
            byte[] buffer=new byte[32768];
            try(RandomAccessFile input=new RandomAccessFile(item.file,"r")){
                long at=first;
                while(at<=last && item==current){
                    long absolute=item.offset+at;
                    int piece=(int)(absolute/item.info.pieceLength());
                    if(!awaitPiece(item,piece,105000))break;
                    long remainPiece=item.info.pieceLength()-(absolute%item.info.pieceLength());
                    int amount=(int)Math.min(Math.min(buffer.length,remainPiece),last-at+1);
                    input.seek(at);
                    int read=input.read(buffer,0,amount);
                    if(read<=0)break;
                    out.write(buffer,0,read);
                    at+=read;
                }
            }catch(FileNotFoundException ignored){
                // torrent may not create the file immediately; ExoPlayer may retry
            }catch(SocketException ignored){
                // client cancelled playback or performed a seek
            }
        }catch(Exception ignored){}
    }
    private boolean awaitPiece(PlayItem item,int wanted,long timeout)throws InterruptedException{
        int count=item.info.numPieces();
        if(wanted<0||wanted>=count)return false;
        long deadline=System.currentTimeMillis()+timeout;
        int ahead=Math.min(8,Math.max(2,1024*1024/item.info.pieceLength()));
        while(System.currentTimeMillis()<deadline && item==current){
            if(item.handle.havePiece(wanted))return true;
            for(int i=0;i<ahead&&wanted+i<count;i++){
                if(!item.handle.havePiece(wanted+i))item.handle.setPieceDeadline(wanted+i,800+i*200);
            }
            Thread.sleep(180);
        }
        return false;
    }
    private void error(Socket socket,int code,String name)throws IOException{
        byte[] bytes=(name+"\n").getBytes(StandardCharsets.UTF_8);
        String header="HTTP/1.1 "+code+" "+name+"\r\nContent-Length: "+bytes.length+
            "\r\nConnection: close\r\nContent-Type: text/plain\r\n\r\n";
        socket.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().write(bytes);
    }
}
