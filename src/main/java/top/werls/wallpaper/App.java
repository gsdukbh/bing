package top.werls.wallpaper;

import com.alibaba.fastjson.*;
import com.alibaba.fastjson.annotation.JSONField;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * @author leejiawei
 */
public class App {


    private static final String UHD_WIDTH = "3840";
    private static final String UHD_HEIGHT = "2160";
    private static final String YING_URL = "https://cn.bing.com/HPImageArchive.aspx?format=js&n=1&uhd=1&uhdwidth=" + UHD_WIDTH + "&uhdheight=" + UHD_HEIGHT;

    private static final String BASIS_URL = "https://cn.bing.com";

    public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36";

    public static final int JSON_SIZE = 1000;

    public static final String JSON_NAME = "images.json";

    public static final String FILE_INDEX = "index.txt";

    public static final String README = "README.md";

    public static final String CONNECT = "jdbc:sqlite:sqlite.db";

    /**
     * images 对象
     */
    public static class Images {
        /**
         * 日期
         */
        @JSONField(format = "yyyy-MM-dd")
        private Date endDate;

        /**
         * url
         */
        private String url;
        /**
         * 版权信息
         */
        private String copyright;
        /**
         * hash 值
         */
        private String hash;

        @Override
        public String toString() {
            return "Images{" +
                    "endDate=" + endDate +
                    ", url='" + url + '\'' +
                    ", copyright='" + copyright + '\'' +
                    ", hash='" + hash + '\'' +
                    '}';
        }

        public Date getEndDate() {
            return endDate;
        }

        public void setEndDate(Date endDate) {
            this.endDate = endDate;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getCopyright() {
            return copyright;
        }

        public void setCopyright(String copyright) {
            this.copyright = copyright;
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public Images() {
        }
    }

    /**
     * 获取图片信息
     *
     * @return
     * @throws Exception
     */
    public static Images getImages() throws Exception {
        Images images = new Images();

//        创建httpclient 请求
        HttpClient client = HttpClient.newBuilder().build();

        HttpRequest request = HttpRequest.newBuilder()
                .header("User-Agent", USER_AGENT)
                .uri(URI.create(YING_URL))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JSONArray imagesJson = JSON.parseObject(response.body()).getJSONArray("images");
        JSONObject jsonObject = imagesJson.getJSONObject(0);

        DateFormat fmt = new SimpleDateFormat("yyyyMMdd");
        images.setEndDate(fmt.parse(jsonObject.getString("enddate")));
        images.setUrl(jsonObject.getString("url"));
        images.setCopyright(jsonObject.getString("copyright"));
        images.setHash(jsonObject.getString("hsh"));
        return images;
    }

    /**
     * 下载文件
     *
     * @param url
     * @throws Exception
     */
    public static void downloadFile(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().build();

        // 4k
        HttpRequest request = HttpRequest.newBuilder()
                .header("User-Agent", USER_AGENT)
                .uri(URI.create(BASIS_URL + url))
                .build();

        // 原图
        url = url.substring(0, url.indexOf("&"));
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .header("User-Agent", USER_AGENT)
                .uri(URI.create(BASIS_URL + url))
                .build();

        String fileName = url.replace("/th?id=", "");
        Path path = Paths.get("images/4k_" + fileName);

        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(path));

        Path filePath = Paths.get("images/bing_" + fileName);

        HttpResponse<Path> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofFile(filePath));

    }

    /**
     * 追加写入readme
     *
     * @param images images object
     * @throws Exception writer
     */
    public static void writeMd(Images images) throws Exception {
        //
        File file = new File(README);
        FileWriter fileWriter = new FileWriter(file, true);
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        String url = images.getUrl().substring(0, images.getUrl().indexOf("&"));
        String md = "| ![" + images.getCopyright() + "]" + "(" + BASIS_URL + images.getUrl() + ") "
                + fmt.format(images.getEndDate()) + images.getCopyright() + "  [ download ](" + BASIS_URL + url + ") |";
        fileWriter.write("\n");
        fileWriter.write(md);
        fileWriter.close();
    }

    /**
     * 读取 json
     *
     * @param filePath file
     * @return List<Images>
     * @throws Exception
     */
    public static List<Images> readerJson(String filePath) throws Exception {
        File file = new File(filePath);
        if (!Files.exists(Path.of(filePath))) {
            Files.createFile(Path.of(filePath));
            return new ArrayList<>();
        }
        FileReader reader = new FileReader(file);
        JSONReader jsonReader = new JSONReader(reader);
        List<Images> images = new ArrayList<>();
        jsonReader.startArray();
        if (jsonReader.hasNext()) {
            Images temp = jsonReader.readObject(Images.class);
        }
        jsonReader.endArray();
        jsonReader.close();
        reader.close();
        return images;
    }

    /**
     * 写入 json
     *
     * @param images   list
     * @param filePath file
     * @throws Exception
     */
    public static void writerJson(List<Images> images, String filePath) throws Exception {
        File file = new File(filePath);
        if (!Files.exists(Path.of(filePath))) {
            Files.createFile(Path.of(filePath));
        }
        FileWriter fileWriter = new FileWriter(file);
        JSONWriter jsonWriter = new JSONWriter(fileWriter);
        jsonWriter.writeObject(images);
        jsonWriter.close();
        fileWriter.close();
    }

    /**
     * 保存到sqlite
     *
     * @param images images
     * @throws Exception
     */
    public static void saveToSqlite(Images images) throws Exception {
        Connection connection = DriverManager.getConnection(CONNECT);

        // 创建数据库
        String dateBase = "CREATE TABLE IF NOT EXISTS images (\n" +
                "  \"id\" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n" +
                "  \"hash\" TEXT,\n" +
                "  \"url\" TEXT,\n" +
                "  \"copyright\" TEXT,\n" +
                "  \"endDate\" DATE\n" +
                ")";
        Statement statement = connection.createStatement();
        statement.execute(dateBase);
        statement.close();
        // 保存
        String sql = "INSERT INTO images(hash,url,copyright,endDate) VALUES(?,?,?,?)";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, images.getHash());
        preparedStatement.setString(2, images.getUrl());
        preparedStatement.setString(3, images.getCopyright());
        preparedStatement.setDate(4, new java.sql.Date(images.getEndDate().getTime()));
        preparedStatement.execute();
        preparedStatement.close();
    }


    /**
     * 读取 sqlite
     *
     * @return
     * @throws Exception
     */
    public static List<Images> getFromSqlite() throws Exception {
        Connection connection = DriverManager.getConnection(CONNECT);
        String sql = "SELECT * FROM images";
        List<Images> list = new ArrayList<>();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        while (resultSet.next()) {
            Images images = new Images();
            images.setHash(resultSet.getString("hash"));
            images.setUrl(resultSet.getString("url"));
            images.setCopyright(resultSet.getString("copyright"));
            images.setEndDate(new Date(resultSet.getDate("endDate").getTime()));
            list.add(images);
        }
        return list;
    }


    public static void writeToTxt(String newFile) throws Exception {
        File file = new File(FILE_INDEX);
        FileWriter writer = new FileWriter(file);
        writer.write(newFile);
        writer.close();
    }

    public static String readerTxt() throws Exception {
        File file = new File(FILE_INDEX);
        if (!Files.exists(Path.of(FILE_INDEX))) {
            Files.createFile(Path.of(FILE_INDEX));
        }
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String res = reader.readLine();
        reader.close();
        return res;
    }

    public static String getJsonName() {
        String res = null;
        try {
            res = readerTxt();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res != null ? res : JSON_NAME;
    }

    public static void main(String[] args) throws Exception {
        Images images = getImages();
        downloadFile(images.getUrl());
        saveToSqlite(images);
        writeMd(images);
        String filePath = getJsonName();
        List<Images> imagesList = readerJson(filePath);
        imagesList.add(images);
        if (imagesList.size() > JSON_SIZE) {
            String[] f = getJsonName().split(JSON_NAME);
            String fileName = "";
            if (f.length > 0) {
                int i = Integer.parseInt(f[0]) + 1;
                fileName = i + JSON_NAME;
            } else {
                fileName = 1 + JSON_NAME;
            }
            filePath = fileName;
            imagesList.clear();
            imagesList.add(images);
        }
        writerJson(imagesList, filePath);
    }
}
