package org.jack.wealthflow.service;

import org.jack.wealthflow.mapper.*;
import org.jack.wealthflow.service.impl.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Path;

/** 每个测试创建独立临时SQLite，使用真实MyBatis映射与事务，不启动应用。 */
class AssetDatabaseFixture {
    final JdbcTemplate jdbc;
    final TransactionTemplate transactions;
    final AssetCategoryService categories;
    final AssetSnapshotService snapshots;
    final SystemService system;
    final PendingActionMapper actionMapper;

    AssetDatabaseFixture(Path file) throws Exception {
        var ds = new DriverManagerDataSource("jdbc:sqlite:" + file, "", "");
        ds.setDriverClassName("org.sqlite.JDBC");
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
        jdbc = new JdbcTemplate(ds);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setTypeAliasesPackage("org.jack.wealthflow.model");
        factory.setMapperLocations(new ClassPathResource("mapper/AssetCategoryMapper.xml"),
                new ClassPathResource("mapper/AssetSnapshotMapper.xml"), new ClassPathResource("mapper/PendingActionMapper.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        var categoryMapper = session.getMapper(AssetCategoryMapper.class);
        var snapshotMapper = session.getMapper(AssetSnapshotMapper.class);
        actionMapper = session.getMapper(PendingActionMapper.class);
        categories = new AssetCategoryServiceImpl(categoryMapper);
        snapshots = new AssetSnapshotServiceImpl(snapshotMapper, categoryMapper);
        system = new SystemServiceImpl(categoryMapper, snapshotMapper);
    }
}
