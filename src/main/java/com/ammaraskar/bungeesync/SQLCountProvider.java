package com.ammaraskar.bungeesync;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SQLCountProvider implements ServerCountProvider, Runnable {

    public static final String TABLE_NAME = "bungee_player_count";

    private BungeeCountSync plugin;
    private Connection connection;
    private int count;

    public SQLCountProvider(BungeeCountSync plugin, Map<?, ?> config) throws ClassNotFoundException, SQLException {
        this.plugin = plugin;

        Map<?, ?> sqlConfig = (Map<?, ?>) config.get("mysql");
        String host = (String) sqlConfig.get("host");
        int port = (Integer) sqlConfig.get("port");
        String database = (String) sqlConfig.get("database");
        String user = (String) sqlConfig.get("user");
        String password = (String) sqlConfig.get("password");

        Class.forName("com.mysql.jdbc.Driver");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true";

        this.connection = DriverManager.getConnection(url, user, password);

        final ResultSet tableExists = this.connection.getMetaData().getTables(null, null, TABLE_NAME, null);
        if (!tableExists.first()) {
            this.connection.createStatement().executeUpdate(create);
        }

        plugin.getProxy().getScheduler().schedule(plugin, this, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public int getTotalCount(int currentServerCount) {
        return this.count + currentServerCount;
    }

    @Override
    public void run() {
        String sql = "SELECT sum(`count`) FROM `" + TABLE_NAME + "` WHERE `server` != ? AND `lastUpdate` >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)";
        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, plugin.getServerName());

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                this.count = rs.getInt(1);
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        sql = "INSERT INTO `" + TABLE_NAME + "` (`server`, `count`) VALUES(?, ?) " +
                "ON DUPLICATE KEY UPDATE `count`=?";
        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, plugin.getServerName());
            ps.setInt(2, plugin.getProxy().getOnlineCount());
            ps.setInt(3, plugin.getProxy().getOnlineCount());

            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static final String create = "CREATE TABLE IF NOT EXISTS `" + TABLE_NAME + "` (" +
            "  `server` varchar(200) NOT NULL DEFAULT ''," +
            "  `count` int(11)," +
            "  `lastUpdate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "  PRIMARY KEY (`server`)" +
            "  ) ENGINE=MyISAM  DEFAULT CHARSET=utf8;";

}
