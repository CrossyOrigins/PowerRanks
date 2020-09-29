package nl.svenar.PowerRanks.database;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import nl.svenar.PowerRanks.PowerRanks;
import nl.svenar.PowerRanks.PowerRanks.StorageType;
import nl.svenar.PowerRanks.PowerRanksExceptionsHandler;

public class PowerDatabase {
	private Connection mysqlConnection;
	private String host, database, username, password, table_users, table_ranks, table_usertags, table_data;
	private int port;
	private StorageType storageType;
	private PowerRanks plugin;

	public PowerDatabase(PowerRanks plugin, StorageType storageType, String host, int port, String username, String password, String database) {
		this.host = host;
		this.port = port;
		this.database = database;
		this.username = username;
		this.password = password;
		this.storageType = storageType;
		this.plugin = plugin;

		this.table_users = "users";
		this.table_ranks = "ranks";
		this.table_usertags = "usertags";
		this.table_data = "data";
	}

	public boolean connectMYSQL() {
		if (storageType == StorageType.MySQL) {
			try {
				synchronized (this) {
					if (getMYSQLConnection() != null && !getMYSQLConnection().isClosed()) {
						return false;
					}

					Class.forName("com.mysql.jdbc.Driver");
					this.mysqlConnection = DriverManager.getConnection("jdbc:mysql://" + this.host + ":" + this.port/* + "/" + this.database */ + "?autoReconnect=true&useSSL=false", this.username, this.password);

					setupMYSQLDatabase();

//					PowerRanks.log.info(ChatColor.GREEN + "MYSQL CONNECTED");
				}
			} catch (SQLException e) {
				PowerRanksExceptionsHandler.except(this.getClass().getName(), e.toString());
				return false;
			} catch (ClassNotFoundException e) {
				PowerRanksExceptionsHandler.except(this.getClass().getName(), e.toString());
				return false;
			}
		}
		return true;
	}

	public boolean connectSQLITE() {
		return false; // TODO: SQLite
	}

	private void setupMYSQLDatabase() throws SQLException {
		String sql_create_database = "CREATE DATABASE " + this.database;

		String sql_create_table_users = "CREATE TABLE `" + this.database + "`.`" + this.table_users
				+ "` ( `uuid` VARCHAR(50) NOT NULL , `name` VARCHAR(30) NOT NULL , `rank` VARCHAR(32) NOT NULL , `subranks` LONGTEXT NOT NULL , `usertag` VARCHAR(32) NOT NULL , `permissions` LONGTEXT NOT NULL , `playtime` INT NOT NULL , UNIQUE `uuid` (`uuid`));";

		String sql_create_table_ranks = "CREATE TABLE `" + this.database + "`.`" + this.table_ranks
				+ "` ( `name` VARCHAR(32) NOT NULL , `permissions` LONGTEXT NOT NULL , `inheritance` LONGTEXT NOT NULL , `build` BOOLEAN NOT NULL , `prefix` VARCHAR(64) NOT NULL , `suffix` VARCHAR(64) NOT NULL , `chat_color` VARCHAR(16) NOT NULL , `name_color` VARCHAR(16) NOT NULL , `level_promote` VARCHAR(32) NOT NULL , `level_demote` VARCHAR(32) NOT NULL , `economy_buyable` LONGTEXT NOT NULL , `economy_cost` INT NOT NULL , `gui_icon` VARCHAR(32) NOT NULL , UNIQUE `name` (`name`));";

		String sql_create_table_usertags = "CREATE TABLE `" + this.database + "`.`" + this.table_usertags + "` ( `name` VARCHAR(32) NOT NULL , `value` VARCHAR(64) NOT NULL , UNIQUE `name` (`name`));";

		String sql_create_table_data = "CREATE TABLE `" + this.database + "`.`" + this.table_data + "` ( `key` VARCHAR(32) NOT NULL , `value` VARCHAR(64) NOT NULL , UNIQUE `key` (`key`));";

		ResultSet resultSet = this.mysqlConnection.getMetaData().getCatalogs();
		boolean exists = false;
		while (resultSet.next()) {

			String databaseName = resultSet.getString(1);
			if (databaseName.equals(this.database)) {
				exists = true;
				break;
			}
		}
		resultSet.close();

		if (!exists) {
			int result = -1;
			result = this.mysqlConnection.createStatement().executeUpdate(sql_create_database);

			PowerRanks.log.info("===--------------------===");
			if (result == 1) {
				PowerRanks.log.info("Database: " + this.database + " created!");

				result = this.mysqlConnection.createStatement().executeUpdate(sql_create_table_users);
				PowerRanks.log.info("Table: " + this.table_users + " created! (status: " + result + ")");

				result = this.mysqlConnection.createStatement().executeUpdate(sql_create_table_ranks);
				PowerRanks.log.info("Table: " + this.table_ranks + " created! (status: " + result + ")");

				result = this.mysqlConnection.createStatement().executeUpdate(sql_create_table_usertags);
				PowerRanks.log.info("Table: " + this.table_usertags + " created! (status: " + result + ")");

				result = this.mysqlConnection.createStatement().executeUpdate(sql_create_table_data);
				PowerRanks.log.info("Table: " + this.table_data + " created! (status: " + result + ")");

				setupMYSQLDefaultData();

			} else {
				PowerRanksExceptionsHandler.exceptCustom(this.getClass().getName(), "There was a error creating the database: " + this.database + " are the permissions set correctly?");
			}
			PowerRanks.log.info("===--------------------===");
		}
	}

	private void setupMYSQLDefaultData() throws SQLException {
		copyTmpFile(plugin, "Ranks.yml");
		final File tmpFile = new File(plugin.getDataFolder() + File.separator + "tmp", "Ranks.yml");
		final YamlConfiguration tmpYamlConf = new YamlConfiguration();
		try {
			tmpYamlConf.load(tmpFile);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}

		String sql_set_default_rank = "INSERT INTO `" + this.database + "`.`" + this.table_data + "`(`key`, `value`) VALUES ('default_rank', '" + tmpYamlConf.getString("Default") + "');";
		String sql_create_rank = "INSERT INTO `" + this.database + "`.`" + this.table_ranks
				+ "` (`name`, `permissions`, `inheritance`, `build`, `prefix`, `suffix`, `chat_color`, `name_color`, `level_promote`, `level_demote`, `economy_buyable`, `economy_cost`, `gui_icon`) VALUES ('%rank_name%', '%rank_permissions%', '%rank_inheritance%', '%rank_build%', '%rank_prefix%', '%rank_suffix%', '%rank_chatcolor%', '%rank_namecolor%', '%rank_promote%', '%rank_demote%', '%rank_buyable%', '%rank_cost%', '%rank_gui_icon%')";
		this.mysqlConnection.createStatement().executeUpdate(sql_set_default_rank);

		for (String key : tmpYamlConf.getConfigurationSection("Groups").getKeys(false)) {
			this.mysqlConnection.createStatement().executeUpdate(sql_create_rank
					.replace("%rank_name%", key)
					.replace("%rank_permissions%", "")
					.replace("%rank_inheritance%", "")
					.replace("%rank_build%", tmpYamlConf.getBoolean("Groups." + key + ".build") ? "1" : "0")
					.replace("%rank_prefix%", (String) tmpYamlConf.get("Groups." + key + ".chat.prefix"))
					.replace("%rank_suffix%", (String) tmpYamlConf.get("Groups." + key + ".chat.suffix"))
					.replace("%rank_chatcolor%", (String) tmpYamlConf.get("Groups." + key + ".chat.chatColor"))
					.replace("%rank_namecolor%", (String) tmpYamlConf.get("Groups." + key + ".chat.nameColor"))
					.replace("%rank_promote%", (String) tmpYamlConf.get("Groups." + key + ".level.promote"))
					.replace("%rank_demote%", (String) tmpYamlConf.get("Groups." + key + ".level.demote"))
					.replace("%rank_buyable%", "")
					.replace("%rank_cost%", "0")
					.replace("%rank_gui_icon%", (String) tmpYamlConf.get("Groups." + key + ".gui.icon"))
					);
		}
		deleteTmpFile(plugin, "Ranks.yml");
	}
	
	public String getDefaultRank() {
		String default_rank = "";
		String sql_get_default_rank = "SELECT `value` FROM `" + this.database + "`.`" + this.table_data + "` WHERE `key`='default_rank';";
		try {
			Statement st = this.mysqlConnection.createStatement();
			ResultSet rs = st.executeQuery(sql_get_default_rank);
		    while (rs.next()) {
		    	default_rank = rs.getString("value");
		    }
		    st.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return default_rank;
	}
	
	public void updatePlayer(Player player, String field, String value) {
		String sql_insert_new_player = "INSERT INTO `" + this.database + "`.`" + this.table_users + "` (`uuid`, `name`, `rank`, `subranks`, `usertag`, `permissions`, `playtime`) VALUES ('%player_uuid%', '%player_name%', '%player_rank%', '%player_subranks%', '%player_usertag%', '%player_permissions%', '%player_playtime%')";
		if (!playerExists(player)) {
			PowerRanks.log.info("---------- Player created");
			PowerRanks.log.warning(sql_insert_new_player
						.replace("%player_uuid%", player.getUniqueId().toString())
						.replace("%player_name%", player.getName())
						.replace("%player_rank%", getDefaultRank())
						.replace("%player_subranks%", "")
						.replace("%player_usertag%", "")
						.replace("%player_permissions%", "")
						.replace("%player_playtime%", "0"));
			try {
				this.mysqlConnection.createStatement().executeUpdate(sql_insert_new_player
						.replace("%player_uuid%", player.getUniqueId().toString())
						.replace("%player_name%", player.getName())
						.replace("%player_rank%", getDefaultRank())
						.replace("%player_subranks%", "")
						.replace("%player_usertag%", "")
						.replace("%player_permissions%", "")
						.replace("%player_playtime%", "0")
						);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else {
			PowerRanks.log.info("---------- Player exists");
		}
	}
	
	public boolean playerExists(Player player) {
		try {
			Statement st = this.mysqlConnection.createStatement();
			ResultSet rs = st.executeQuery("SELECT * FROM `" + this.database + "`.`" + this.table_users + "` WHERE `uuid`='" + player.getUniqueId().toString() + "';");
			if (rs.next()) {
				return true;
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public Connection getMYSQLConnection() {
		return mysqlConnection;
	}

	public boolean isDatabase() {
		return storageType == StorageType.MySQL || storageType == StorageType.SQLite;
	}

	private void copyTmpFile(PowerRanks plugin, String yamlFileName) {
		File tmp_file = new File(plugin.getDataFolder() + File.separator + "tmp", yamlFileName);
		if (!tmp_file.exists())
			tmp_file.getParentFile().mkdirs();
		plugin.copy(plugin.getResource(yamlFileName), tmp_file);
	}
	
	private void deleteTmpFile(PowerRanks plugin, String yamlFileName) {
		File tmp_file = new File(plugin.getDataFolder() + File.separator + "tmp", yamlFileName);
		if (tmp_file.exists())
			tmp_file.delete();
	}
}
