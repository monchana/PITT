package PITT;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.text.*;

public class HTTPInterpreter{
  //TODO : interpret parsed Event
  private static final boolean THREAD_POOL[] = new boolean[FileThread.THREAD_MAX];

//  public static Response create_response(Event http_request){
//    //only for NON_IO, (IO?)
//    System.out.println("creating response");
//    SocketChannel client = http_request.client;
//    SelectionKey key = http_request.key;
//    public int limit;
//    public int counter;
//    private int thread_array [limit] = [0];
//    String http_version = "HTTP/1.1";
//    int status_code;
//    TreeMap<String,String> header_map = new TreeMap<String,String>();
//    ByteBuffer body = ByteBuffer.allocate(Global.BUFFER_SIZE);
//
//    /** case : parse error occurred */
//    if(http_request.error_code != 200){
//      status_code = http_request.error_code;
//      String status_str = Global.http_status_map.get(status_code);
//
//      //TODO : study how to use ByteBuffer!!!!!!!
//      //first line + header
//      header_map.put("a","b");
//
//      return new Response(client,key,http_version,status_code,header_map);
//    }
//
//    /** parser error code 200 */
//    status_code = 200; //TODO
//
//    //append headers???
//    header_map.put("Connection","keep-alive");
//
//    /*process body*/
//    //1. check cache
//    if(Cache.has(http_request.uri)){
//      body.put(
//              Cache.get(http_request.uri)
//      );
//    }
//    else {
//      //involves file
//      String filename = http_request.uri.substring(1);
//      File file = new File(filename);
//
//      //1. 404 //? does it not require finding? thread burden?
//      if(!file.exists()){
//        status_code = 404;
//        return new Response(client, key, http_version, 404, header_map);
//      }
//
//      //2. 304
//      if(try304(http_request,file)){//debug needed
//        //TODO : 304
//        status_code = 304;
//        return new Response(client, key, http_version, 304, header_map);
//      }
//
//      //other headers?
//      //206
//      if(http_request.header_map.containsKey("Range")){
//        //TODO : note that there are If-Range, Content-Range, Range headers
//        // read https://svn.apache.org/repos/asf/labs/webarch/trunk/http/draft-fielding-http/p5-range.html for detail
//        /*
//        under the assumption
//
//        String range = header_map.get("Range");
//        int i = range.indexOf("=");
//        int j = range.indexOf("-");
//
//        long start = Long.parseLong(range.substring(i + 1, j));
//        long end = 0;
//        if (j < range.length() - 1) {
//          end = Long.parseLong(range.substring(j + 1));
//        }
//        if (end == 0) {
//          end = start + 2 * 1024 * 1024 - 1;
//        }
//        if (end > file.length() - 1) {
//          end = file.length() - 1;
//        }
//        */
//        status_code = 206;
//      }
//
//      /*else {
//        data_code = 200
//      }
//      */
//
//      ///////////////////////////////////////////////////////////////
//      body.put(
//              ("Not Implemented yet sorry...").getBytes()
//      );
//    }
//
//    return new Response(client,key,http_version,status_code,header_map,body);
//  }

  public static String create_response_NON_IO(Event http_request){
    //only for NON_IO, (IO?)
    System.out.println("creating NON_IO response");
    SocketChannel client = http_request.client;
    SelectionKey key = http_request.key;

    TreeMap<String,String> header_map = new TreeMap<String,String>();
    ByteBuffer body = ByteBuffer.allocate(Global.BUFFER_SIZE);

    int status_code = http_request.error_code;
    String status_str = Global.http_status_map.get(status_code);
    String http_version = "HTTP/1.1";

    String first_line = status_code + " " + status_str + " " + http_version;


    //TODO : header & body
    //TODO : study how to use ByteBuffer!!!!!!!
    //first line + header
    header_map.put("a","b");

    return first_line;//TODO header & body
  }

  public static Event respond(Event http_request, EventQueue EVENT_QUEUE){
    System.out.println("respond responding...");
    System.out.println(http_request.error_code);
    Event.Type type = http_request.type;
    SocketChannel client = http_request.client;
    SelectionKey key = http_request.key;

    ByteBuffer buffer = ByteBuffer.allocate(0);
    try{
      if(type == Event.Type.NON_IO){
        System.out.println("NON IO TYPE");
        String response_str = create_response_NON_IO(http_request);
        //System.out.println(response_str);
        buffer.put(response_str.getBytes());

        while(buffer.hasRemaining()){ //TODO : temporarily, write to client with while loop
          int x = client.write(buffer);
        }

        String connection = http_request.connection;
        handle_connection(http_request);
      }
      else if(type == Event.Type.CONTINUATION){
        System.out.println("CONTINUATION TYPE");
        System.out.println("THREADING START");
        //TODO : cache maintenance

        int thread_num = free_thread();
        if(thread_num != -1){
          THREAD_POOL[thread_num] = true;
          FileThread f = new FileThread(thread_num, http_request, EVENT_QUEUE);
          f.start();
          THREAD_POOL[thread_num] = false;
        }
        else{
          System.out.println("Thread full, reenqueueing to the queue");
          EVENT_QUEUE.push(http_request);
        }
        return null;
      }
      //io
      else if(type == Event.Type.IO){
        System.out.println("IO TYPE");
        //cacheing
        if(Cache.has(http_request.uri)){
          buffer = Cache.get(http_request.uri); //TODO : copy not aliasing
        }
        else{
          System.out.println("THREADING START");
          //TODO : cache maintenance

          int thread_num = free_thread();
          if(thread_num != -1){
            THREAD_POOL[thread_num] = true;
            FileThread f = new FileThread(thread_num, http_request, EVENT_QUEUE);
            f.start();
            THREAD_POOL[thread_num] = false;
          }
          else{
            System.out.println("Thread full, reenqueueing to the queue");
            EVENT_QUEUE.push(http_request);
          }
          return null;
        }
      }
    }
    catch(Exception ex){
      //TODO
    }

    return null;
  }

  private static int free_thread(){//if unavailable, return -1
    for(int i=0;i<THREAD_POOL.length;i++){
      if(THREAD_POOL[i] == false){
        return i;
      }
    }

    return -1;
  }

  public static boolean try304(Event http_request, File file){//TODO : confirm logic
    if(http_request.header_map.containsKey("If-Modified-Since")){//debug needed
      String date_string = http_request.header_map.get("If-Modified-Since");
      try{
        //parse request modified date
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
        Date req_date = format.parse(date_string);

        //check file
        Date file_date = new Date(file.lastModified());
        //String file_date = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date(file.lastModified()));

        if(req_date.equals(file_date)){//does this work?
          return true;
        }
      }
      catch(ParseException pe){// or other exception
        // date format invalid.
        // ignore if-modified-since header?
        // or make this 400

      }
    }

    //304 failed
    return false;
  }

  private static void handle_connection(Event http_request){
    //TODO : twisted logic... i don't know
    Event.Type type = http_request.type;
    if(!(type == Event.Type.IO || type == Event.Type.NON_IO)){
      return;
    }

    SocketChannel client = http_request.client;
    //SelectionKey key = http_request.key;
    if(http_request.header_map.containsKey("Connection") &&
            http_request.header_map.get("Connection").equals("keep-alive")){
      //TODO
      return; //keep-alive!
    }

    //close
    try {
      client.close();
    }
    catch(IOException ex){
      //TODO
    }

  }
}

class FileThread extends Thread{
  public static final int THREAD_MAX = 4;
  private static int THREAD_COUNT = 0;
  public int thread_number;

  Event event;
  EventQueue event_queue;
  FileChannel errorChannel;
  File file;
 // MappedByteBuffer buffer_file; //buffer file of error 400, 404, 405

  public FileThread(int thread_number, Event event, EventQueue event_queue){
    this.thread_number = thread_number;

    this.event = event;
    this.event_queue = event_queue;
  }

  //additional test conducted : see for change
  public void run(){
    //TODO
    THREAD_COUNT++; //manage counter

    //System.out.println("IO Thread : " + thread_number + " Start");
    /** NAHYUNSOO : do it ! ********************************************************************/

    /** 1. open file from event */
    String filename = event.uri.substring(1); 
    this.file = new File(filename);
    ByteBuffer buffer = null;
    int read_start, read_end; // read range

    //1. 404
    if(!file.exists()){
      //404
    }

    //2. 304
    if(true){

    }


    //not 404 nor 304

//    FileChannel inChannel = new FileInputStream(fileName).getChannel();
//    //event.size = (int) inChannel.size(); // size of event
//    //Cache.set(event.uri, buffer);
//
//    // first time io
//    if (event.type==Event.Type.IO){
//      if ((file.length() >= Global.BUFFER_SIZE){ // goes in from start IO
//        buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, start, Global.BUFFER_SIZE);
//        //Mark that it is only read to certain point : call this marker
//        event_queue.push(event);
//      }
//      else{
//        buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, start, end-start);
//      }
//    }
//    else {
//      if ((file.length()-marker)>=Global.BUFFER_SIZE){
//        buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, marker, Global.BUFFER_SIZE);
//        event_queue.push(event);
//      }
//      else {
//        buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, marker, end-marker);
//      }
//    }
//
//    Cache.set(event.uri, buffer);
//    //Need to return the range of file;
//    body.put{
//      buffer;
//    }
//    inChannel.close();
//
//
//    //2. create response message buffer
//
//    //3.
//    SocketChannel client = event.client;
//    SelectionKey key = event.key;
//    /*
//    Consider Range : how much to return
//    */
//    try {
//      if (buffer.hasRemaining()) {
//        event_queue.push(new Event(client, key, buffer, event.connection));
//      }
//    // ProcessEvent(event);
//
//    /*
//    has a data buffer : require return header
//    header buffer.flip : get the last info;
//    long size = header.flip.limit() + buffer.flip.limit();
//    or
//    long size = header.pos() + buffer.pos();
//
//    long input_size = client.write(data)
//
//    if (input_size < size){
//    body.put(header);
//    body.put(buffer);
//    event.key.attach(event);
//    }
//    try {
//    event.key.interestOps(SelectionKey.OP_WRITE);
//    event.key.selector().wakeup();
//    */
//
//    }
//    catch(Exception e){//IOException | InterruptedException e
//      System.out.println("error occurred! at : ");
//      e.printStackTrace();
//      event.key.attach(null);
//      event.key.cancel();
//      event.key.channel().close();
//    }
    /*
    public boolean modified(Event event) throws IOException, InterruptedException {
    File file = new File(event.uri.substring(1));
    if (file.exists()) {
    System.out.println("File Exist");
    String date = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date(file.lastModified()));
    event.header_map.put("If-Modified-Since", date);
    return true;
    } else {
    return false;
    }*/
    System.out.println("IO Thread" + thread_number+ "End");

    /************************************************************************/
    THREAD_COUNT--; //manage counter
  }
}

//byte[] bytes = new byte[buffer.position()];
//buffer.flip();
//buffer.get(bytes);
//System.out.println("response buffer is : " + new String(bytes));
//System.out.println(http_request.http_version.length());