/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.destination.hdfs;

import com.streamsets.pipeline.api.OnRecordError;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.config.AvroCompression;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.lib.util.SdcAvroTestUtil;
import com.streamsets.pipeline.sdk.TargetRunner;
import com.streamsets.pipeline.stage.destination.hdfs.util.HdfsTargetUtil;
import com.streamsets.pipeline.stage.destination.lib.DataGeneratorFormatConfig;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class TestHdfsTargetAvro {

  private static String testDir;

  @Before
  public void setUpClass() {
    File dir = new File("target", UUID.randomUUID().toString()).getAbsoluteFile();
    Assert.assertTrue(dir.mkdirs());
    testDir = dir.getAbsolutePath();
  }
  private String getTestDir() {
    return testDir;
  }

  @Test
  public void testHdfsTargetAvro() throws Exception {

    String dirPathTemplate = getTestDir() + "/hdfs/";

    DataGeneratorFormatConfig dataGeneratorFormatConfig = new DataGeneratorFormatConfig();
    dataGeneratorFormatConfig.avroSchema = SdcAvroTestUtil.AVRO_SCHEMA1;
    dataGeneratorFormatConfig.avroCompression = AvroCompression.NULL;

    HdfsTarget hdfsTarget = HdfsTargetUtil.createHdfsTarget(
      "file:///",
      "foo",
      false,
      null,
      new HashMap<String, String>(),
      "foo",
      "UTC",
      dirPathTemplate,
      HdfsFileType.TEXT,
      "${uuid()}",
      CompressionMode.NONE,
      HdfsSequenceFileCompressionType.BLOCK,
      3,
      0,
      "${time:now()}",
      "${30 * MINUTES}",
      LateRecordsAction.SEND_TO_ERROR,
      "",
      DataFormat.AVRO,
      dataGeneratorFormatConfig
    );

    TargetRunner runner = new TargetRunner.Builder(HdfsDTarget.class, hdfsTarget)
      .setOnRecordError(OnRecordError.STOP_PIPELINE)
      .build();
    runner.runInit();

    List<Record> records = SdcAvroTestUtil.getRecords1();

    runner.runWrite(records);
    runner.runDestroy();

    //read the avro data file generated by and verify the records
    File dir = new File(dirPathTemplate);
    Assert.assertTrue(dir.exists());
    Assert.assertTrue(dir.isDirectory());

    String[] files = dir.list(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        if(name.startsWith("foo")) {
          return true;
        }
        return false;
      }
    });
    Assert.assertEquals(1, files.length);

    File avroDataFile = new File(dirPathTemplate, files[0]);
    Schema schema = new Schema.Parser().parse(SdcAvroTestUtil.AVRO_SCHEMA1);
    DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema);
    DataFileReader<GenericRecord> dataFileReader = new DataFileReader<>(avroDataFile, datumReader);
    int actualRecordCount = 0;
    List<GenericRecord> genericRecords = new ArrayList<>();
    while(dataFileReader.hasNext()) {
      genericRecords.add(dataFileReader.next());
      actualRecordCount++;
    }
    Assert.assertEquals(3, actualRecordCount);

    SdcAvroTestUtil.compare1(genericRecords);

  }

}
