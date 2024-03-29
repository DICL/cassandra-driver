/*
 *      Copyright (C) 2012-2015 DataStax Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.datastax.driver.extras.codecs.jdk8;

import com.datastax.driver.core.*;
import com.datastax.driver.core.utils.CassandraVersion;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;

import static com.datastax.driver.core.DataType.timestamp;
import static com.datastax.driver.core.DataType.varchar;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.assertj.core.api.Assertions.assertThat;

@CassandraVersion(major = 2.2)
public class Jdk8TimeCodecsTest extends CCMBridge.PerClassSingleNodeCluster {

    @Override
    protected Collection<String> getTableDefinitions() {
        return Collections.singletonList(
                "CREATE TABLE IF NOT EXISTS foo ("
                        + "c1 text PRIMARY KEY, "
                        + "cdate date, "
                        + "ctime time, "
                        + "ctimestamp timestamp, "
                        + "ctuple tuple<timestamp,varchar>, "
                        + "cdates frozen<list<date>>, "
                        + "ctimes frozen<set<time>>, "
                        + "ctimestamps frozen<map<text,timestamp>>, "
                        + "ctuples frozen<map<tuple<timestamp,varchar>,varchar>>"
                        + ")");
    }

    @BeforeClass(groups = "short")
    public void registerCodecs() throws Exception {
        TupleType dateWithTimeZoneType = cluster.getMetadata().newTupleType(timestamp(), varchar());
        CodecRegistry codecRegistry = cluster.getConfiguration().getCodecRegistry();
        codecRegistry
                .register(LocalTimeCodec.instance)
                .register(LocalDateCodec.instance)
                .register(InstantCodec.instance)
                .register(new ZonedDateTimeCodec(dateWithTimeZoneType));
    }

    /**
     * <p>
     * Validates that a <code>time</code> column can be mapped to a {@link LocalTime} by using
     * {@link LocalTimeCodec}.
     * </p>
     *
     * @test_category data_types:serialization
     * @expected_result properly maps.
     * @jira_ticket JAVA-606
     * @since 2.2.0
     */
    @Test(groups = "short")
    public void should_map_time_to_localtime() {
        // given
        LocalTime time = LocalTime.of(12, 16, 34, 999);
        // when
        session.execute("insert into foo (c1, ctime) values (?, ?)", "should_map_time_to_localtime", time);
        ResultSet result = session.execute("select ctime from foo where c1=?", "should_map_time_to_localtime");
        // then
        assertThat(result.getAvailableWithoutFetching()).isEqualTo(1);
        Row row = result.one();
        assertThat(row.get("ctime", LocalTime.class)).isEqualTo(time);
        assertThat(row.getTime("ctime")).isEqualTo(time.toNanoOfDay());
    }

    /**
     * <p>
     * Validates that a <code>date</code> column can be mapped to a {@link LocalDate} by using
     * {@link LocalDateCodec}.
     * </p>
     *
     * @test_category data_types:serialization
     * @expected_result properly maps.
     * @jira_ticket JAVA-606
     * @since 2.2.0
     */
    @Test(groups = "short")
    public void should_map_date_to_localdate() {
        // given
        LocalDate localDate = LocalDate.of(2015, 1, 1);
        com.datastax.driver.core.LocalDate driverLocalDate = com.datastax.driver.core.LocalDate.fromYearMonthDay(2015, 1, 1);
        // when
        session.execute("insert into foo (c1, cdate) values (?, ?)", "should_map_date_to_localdate", localDate);
        ResultSet result = session.execute("select cdate from foo where c1=?", "should_map_date_to_localdate");
        // then
        assertThat(result.getAvailableWithoutFetching()).isEqualTo(1);
        Row row = result.one();
        assertThat(row.get("cdate", LocalDate.class)).isEqualTo(localDate);
        assertThat(row.getDate("cdate")).isEqualTo(driverLocalDate);
    }

    /**
     * <p>
     * Validates that a <code>timestamp</code> column can be mapped to a {@link Instant} by using
     * {@link InstantCodec}.
     * </p>
     *
     * @test_category data_types:serialization
     * @expected_result properly maps.
     * @jira_ticket JAVA-606
     * @since 2.2.0
     */
    @Test(groups = "short")
    public void should_map_timestamp_to_instant() {
        // given
        Instant instant = Instant.parse("2010-06-30T01:20:30Z");
        // when
        session.execute("insert into foo (c1, ctimestamp) values (?, ?)", "should_map_timestamp_to_instant", instant);
        ResultSet result = session.execute("select ctimestamp from foo where c1=?", "should_map_timestamp_to_instant");
        // then
        assertThat(result.getAvailableWithoutFetching()).isEqualTo(1);
        Row row = result.one();
        assertThat(row.get("ctimestamp", Instant.class)).isEqualTo(instant);
        assertThat(row.getTimestamp("ctimestamp")).isEqualTo(Date.from(instant));
    }

    /**
     * <p>
     * Validates that a <code>tuple&lt;timestamp,text&gt;</code> column can be mapped to a {@link ZonedDateTime} by using
     * {@link ZonedDateTimeCodec}.
     * </p>
     *
     * @test_category data_types:serialization
     * @expected_result properly maps.
     * @jira_ticket JAVA-606
     * @since 2.2.0
     */
    @Test(groups = "short")
    public void should_map_tuple_to_zoneddatetime() {
        // given
        ZonedDateTime expected = ZonedDateTime.parse("2010-06-30T01:20+05:30");
        // when
        PreparedStatement insertStmt = session.prepare("insert into foo (c1, ctuple) values (?, ?)");
        session.execute(insertStmt.bind("should_map_tuple_to_instant", expected));
        ResultSet result = session.execute("select ctuple from foo where c1=?", "should_map_tuple_to_instant");
        // then
        assertThat(result.getAvailableWithoutFetching()).isEqualTo(1);
        Row row = result.one();
        ZonedDateTime actual = row.get("ctuple", ZonedDateTime.class);
        // Since timezone is preserved in the tuple, the date times should match perfectly.
        assertThat(actual).isEqualTo(expected);
        // Ensure the timezones match as well.
        // (This is just a safety check as the previous assert would have failed otherwise).
        assertThat(actual.getZone()).isEqualTo(expected.getZone());
    }

    @Test(groups = "short")
    public void should_use_mapper_to_store_and_retrieve_values_with_custom_jdk8_codecs() {
        // given
        MappingManager manager = new MappingManager(session);
        Mapper<Mapped> mapper = manager.mapper(Mapped.class);
        // when
        Mapped pojo = new Mapped();
        mapper.save(pojo);
        Mapped actual = mapper.get("mapper");
        // then
        assertThat(actual).isEqualToComparingFieldByField(pojo);
    }

    @SuppressWarnings("unused")
    @Table(name = "foo")
    public static class Mapped {

        @PartitionKey
        private String c1;

        @Column(name = "cdate")
        private LocalDate date;

        @Column(name = "ctime")
        private LocalTime time;

        @Column(name = "ctimestamp")
        private Instant instant;

        @Column(name = "ctuple")
        private ZonedDateTime zdt;

        @Column(name = "cdates")
        private List<LocalDate> dates;

        @Column(name = "ctimes")
        private Set<LocalTime> times;

        @Column(name = "ctimestamps")
        private Map<String, Instant> instants;

        @Column(name = "ctuples")
        private Map<ZonedDateTime, String> zdts;

        public Mapped() {
            c1 = "mapper";
            date = LocalDate.parse("2014-01-01");
            time = LocalTime.NOON;
            instant = Instant.parse("2010-06-30T01:20:47.000Z");
            zdt = ZonedDateTime.parse("2010-06-30T01:20:47+05:30");
            dates = newArrayList(date);
            times = newHashSet(time);
            instants = ImmutableMap.of("foo", instant);
            zdts = ImmutableMap.of(zdt, "bar");
        }

        public String getC1() {
            return c1;
        }

        public void setC1(String c1) {
            this.c1 = c1;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public LocalTime getTime() {
            return time;
        }

        public void setTime(LocalTime time) {
            this.time = time;
        }

        public Instant getInstant() {
            return instant;
        }

        public void setInstant(Instant instant) {
            this.instant = instant;
        }

        public ZonedDateTime getZdt() {
            return zdt;
        }

        public void setZdt(ZonedDateTime zdt) {
            this.zdt = zdt;
        }

        public List<LocalDate> getDates() {
            return dates;
        }

        public void setDates(List<LocalDate> dates) {
            this.dates = dates;
        }

        public Set<LocalTime> getTimes() {
            return times;
        }

        public void setTimes(Set<LocalTime> times) {
            this.times = times;
        }

        public Map<String, Instant> getInstants() {
            return instants;
        }

        public void setInstants(Map<String, Instant> instants) {
            this.instants = instants;
        }

        public Map<ZonedDateTime, String> getZdts() {
            return zdts;
        }

        public void setZdts(Map<ZonedDateTime, String> zdts) {
            this.zdts = zdts;
        }
    }
}
