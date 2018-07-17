#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: InstanceCommunication.proto

import sys
_b=sys.version_info[0]<3 and (lambda x:x) or (lambda x:x.encode('latin1'))
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
from google.protobuf import descriptor_pb2
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()


from google.protobuf import empty_pb2 as google_dot_protobuf_dot_empty__pb2


DESCRIPTOR = _descriptor.FileDescriptor(
  name='InstanceCommunication.proto',
  package='proto',
  syntax='proto3',
  serialized_pb=_b('\n\x1bInstanceCommunication.proto\x12\x05proto\x1a\x1bgoogle/protobuf/empty.proto\"\xc6\x05\n\x0e\x46unctionStatus\x12\x0f\n\x07running\x18\x01 \x01(\x08\x12\x18\n\x10\x66\x61ilureException\x18\x02 \x01(\t\x12\x13\n\x0bnumRestarts\x18\x03 \x01(\x03\x12\x14\n\x0cnumProcessed\x18\x04 \x01(\x03\x12 \n\x18numSuccessfullyProcessed\x18\x05 \x01(\x03\x12\x19\n\x11numUserExceptions\x18\x06 \x01(\x03\x12H\n\x14latestUserExceptions\x18\x07 \x03(\x0b\x32*.proto.FunctionStatus.ExceptionInformation\x12\x1b\n\x13numSystemExceptions\x18\x08 \x01(\x03\x12J\n\x16latestSystemExceptions\x18\t \x03(\x0b\x32*.proto.FunctionStatus.ExceptionInformation\x12W\n\x19\x64\x65serializationExceptions\x18\n \x03(\x0b\x32\x34.proto.FunctionStatus.DeserializationExceptionsEntry\x12\x1f\n\x17serializationExceptions\x18\x0b \x01(\x03\x12\x16\n\x0e\x61verageLatency\x18\x0c \x01(\x01\x12\x1a\n\x12lastInvocationTime\x18\r \x01(\x03\x12\x12\n\ninstanceId\x18\x0e \x01(\t\x12#\n\x07metrics\x18\x0f \x01(\x0b\x32\x12.proto.MetricsData\x1a\x45\n\x14\x45xceptionInformation\x12\x17\n\x0f\x65xceptionString\x18\x01 \x01(\t\x12\x14\n\x0cmsSinceEpoch\x18\x02 \x01(\x03\x1a@\n\x1e\x44\x65serializationExceptionsEntry\x12\x0b\n\x03key\x18\x01 \x01(\t\x12\r\n\x05value\x18\x02 \x01(\x03:\x02\x38\x01\"G\n\x12\x46unctionStatusList\x12\x31\n\x12\x66unctionStatusList\x18\x01 \x03(\x0b\x32\x15.proto.FunctionStatus\"\xd2\x01\n\x0bMetricsData\x12\x30\n\x07metrics\x18\x01 \x03(\x0b\x32\x1f.proto.MetricsData.MetricsEntry\x1a\x42\n\nDataDigest\x12\r\n\x05\x63ount\x18\x01 \x01(\x01\x12\x0b\n\x03sum\x18\x02 \x01(\x01\x12\x0b\n\x03max\x18\x03 \x01(\x01\x12\x0b\n\x03min\x18\x04 \x01(\x01\x1aM\n\x0cMetricsEntry\x12\x0b\n\x03key\x18\x01 \x01(\t\x12,\n\x05value\x18\x02 \x01(\x0b\x32\x1d.proto.MetricsData.DataDigest:\x02\x38\x01\"$\n\x11HealthCheckResult\x12\x0f\n\x07success\x18\x01 \x01(\x08\x32\xdc\x02\n\x0fInstanceControl\x12\x44\n\x11GetFunctionStatus\x12\x16.google.protobuf.Empty\x1a\x15.proto.FunctionStatus\"\x00\x12\x42\n\x12GetAndResetMetrics\x12\x16.google.protobuf.Empty\x1a\x12.proto.MetricsData\"\x00\x12@\n\x0cResetMetrics\x12\x16.google.protobuf.Empty\x1a\x16.google.protobuf.Empty\"\x00\x12:\n\nGetMetrics\x12\x16.google.protobuf.Empty\x1a\x12.proto.MetricsData\"\x00\x12\x41\n\x0bHealthCheck\x12\x16.google.protobuf.Empty\x1a\x18.proto.HealthCheckResult\"\x00\x42:\n!org.apache.pulsar.functions.protoB\x15InstanceCommunicationb\x06proto3')
  ,
  dependencies=[google_dot_protobuf_dot_empty__pb2.DESCRIPTOR,])




_FUNCTIONSTATUS_EXCEPTIONINFORMATION = _descriptor.Descriptor(
  name='ExceptionInformation',
  full_name='proto.FunctionStatus.ExceptionInformation',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='exceptionString', full_name='proto.FunctionStatus.ExceptionInformation.exceptionString', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='msSinceEpoch', full_name='proto.FunctionStatus.ExceptionInformation.msSinceEpoch', index=1,
      number=2, type=3, cpp_type=2, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=643,
  serialized_end=712,
)

_FUNCTIONSTATUS_DESERIALIZATIONEXCEPTIONSENTRY = _descriptor.Descriptor(
  name='DeserializationExceptionsEntry',
  full_name='proto.FunctionStatus.DeserializationExceptionsEntry',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='key', full_name='proto.FunctionStatus.DeserializationExceptionsEntry.key', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='value', full_name='proto.FunctionStatus.DeserializationExceptionsEntry.value', index=1,
      number=2, type=3, cpp_type=2, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  options=_descriptor._ParseOptions(descriptor_pb2.MessageOptions(), _b('8\001')),
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=714,
  serialized_end=778,
)

_FUNCTIONSTATUS = _descriptor.Descriptor(
  name='FunctionStatus',
  full_name='proto.FunctionStatus',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='running', full_name='proto.FunctionStatus.running', index=0,
      number=1, type=8, cpp_type=7, label=1,
      has_default_value=False, default_value=False,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='failureException', full_name='proto.FunctionStatus.failureException', index=1,
      number=2, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='numRestarts', full_name='proto.FunctionStatus.numRestarts', index=2,
      number=3, type=3, cpp_type=2, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='numProcessed', full_name='proto.FunctionStatus.numProcessed', index=3,
      number=4, type=3, cpp_type=2, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='numSuccessfullyProcessed', full_name='proto.FunctionStatus.numSuccessfullyProcessed', index=4,
      number=5, type=3, cpp_type=2, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='numUserExceptions', full_name='proto.FunctionStatus.numUserExceptions', index=5,
      number=6, type=3, cpp_type=2, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='latestUserExceptions', full_name='proto.FunctionStatus.latestUserExceptions', index=6,
      number=7, type=11, cpp_type=10, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='numSystemExceptions', full_name='proto.FunctionStatus.numSystemExceptions', index=7,
      number=8, type=3, cpp_type=2, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='latestSystemExceptions', full_name='proto.FunctionStatus.latestSystemExceptions', index=8,
      number=9, type=11, cpp_type=10, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='deserializationExceptions', full_name='proto.FunctionStatus.deserializationExceptions', index=9,
      number=10, type=11, cpp_type=10, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='serializationExceptions', full_name='proto.FunctionStatus.serializationExceptions', index=10,
      number=11, type=3, cpp_type=2, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='averageLatency', full_name='proto.FunctionStatus.averageLatency', index=11,
      number=12, type=1, cpp_type=5, label=1,
      has_default_value=False, default_value=float(0),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='lastInvocationTime', full_name='proto.FunctionStatus.lastInvocationTime', index=12,
      number=13, type=3, cpp_type=2, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='instanceId', full_name='proto.FunctionStatus.instanceId', index=13,
      number=14, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='metrics', full_name='proto.FunctionStatus.metrics', index=14,
      number=15, type=11, cpp_type=10, label=1,
      has_default_value=False, default_value=None,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[_FUNCTIONSTATUS_EXCEPTIONINFORMATION, _FUNCTIONSTATUS_DESERIALIZATIONEXCEPTIONSENTRY, ],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=68,
  serialized_end=778,
)


_FUNCTIONSTATUSLIST = _descriptor.Descriptor(
  name='FunctionStatusList',
  full_name='proto.FunctionStatusList',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='functionStatusList', full_name='proto.FunctionStatusList.functionStatusList', index=0,
      number=1, type=11, cpp_type=10, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=780,
  serialized_end=851,
)


_METRICSDATA_DATADIGEST = _descriptor.Descriptor(
  name='DataDigest',
  full_name='proto.MetricsData.DataDigest',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='count', full_name='proto.MetricsData.DataDigest.count', index=0,
      number=1, type=1, cpp_type=5, label=1,
      has_default_value=False, default_value=float(0),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='sum', full_name='proto.MetricsData.DataDigest.sum', index=1,
      number=2, type=1, cpp_type=5, label=1,
      has_default_value=False, default_value=float(0),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='max', full_name='proto.MetricsData.DataDigest.max', index=2,
      number=3, type=1, cpp_type=5, label=1,
      has_default_value=False, default_value=float(0),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='min', full_name='proto.MetricsData.DataDigest.min', index=3,
      number=4, type=1, cpp_type=5, label=1,
      has_default_value=False, default_value=float(0),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=919,
  serialized_end=985,
)

_METRICSDATA_METRICSENTRY = _descriptor.Descriptor(
  name='MetricsEntry',
  full_name='proto.MetricsData.MetricsEntry',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='key', full_name='proto.MetricsData.MetricsEntry.key', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='value', full_name='proto.MetricsData.MetricsEntry.value', index=1,
      number=2, type=11, cpp_type=10, label=1,
      has_default_value=False, default_value=None,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  options=_descriptor._ParseOptions(descriptor_pb2.MessageOptions(), _b('8\001')),
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=987,
  serialized_end=1064,
)

_METRICSDATA = _descriptor.Descriptor(
  name='MetricsData',
  full_name='proto.MetricsData',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='metrics', full_name='proto.MetricsData.metrics', index=0,
      number=1, type=11, cpp_type=10, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[_METRICSDATA_DATADIGEST, _METRICSDATA_METRICSENTRY, ],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=854,
  serialized_end=1064,
)


_HEALTHCHECKRESULT = _descriptor.Descriptor(
  name='HealthCheckResult',
  full_name='proto.HealthCheckResult',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='success', full_name='proto.HealthCheckResult.success', index=0,
      number=1, type=8, cpp_type=7, label=1,
      has_default_value=False, default_value=False,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=1066,
  serialized_end=1102,
)

_FUNCTIONSTATUS_EXCEPTIONINFORMATION.containing_type = _FUNCTIONSTATUS
_FUNCTIONSTATUS_DESERIALIZATIONEXCEPTIONSENTRY.containing_type = _FUNCTIONSTATUS
_FUNCTIONSTATUS.fields_by_name['latestUserExceptions'].message_type = _FUNCTIONSTATUS_EXCEPTIONINFORMATION
_FUNCTIONSTATUS.fields_by_name['latestSystemExceptions'].message_type = _FUNCTIONSTATUS_EXCEPTIONINFORMATION
_FUNCTIONSTATUS.fields_by_name['deserializationExceptions'].message_type = _FUNCTIONSTATUS_DESERIALIZATIONEXCEPTIONSENTRY
_FUNCTIONSTATUS.fields_by_name['metrics'].message_type = _METRICSDATA
_FUNCTIONSTATUSLIST.fields_by_name['functionStatusList'].message_type = _FUNCTIONSTATUS
_METRICSDATA_DATADIGEST.containing_type = _METRICSDATA
_METRICSDATA_METRICSENTRY.fields_by_name['value'].message_type = _METRICSDATA_DATADIGEST
_METRICSDATA_METRICSENTRY.containing_type = _METRICSDATA
_METRICSDATA.fields_by_name['metrics'].message_type = _METRICSDATA_METRICSENTRY
DESCRIPTOR.message_types_by_name['FunctionStatus'] = _FUNCTIONSTATUS
DESCRIPTOR.message_types_by_name['FunctionStatusList'] = _FUNCTIONSTATUSLIST
DESCRIPTOR.message_types_by_name['MetricsData'] = _METRICSDATA
DESCRIPTOR.message_types_by_name['HealthCheckResult'] = _HEALTHCHECKRESULT
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

FunctionStatus = _reflection.GeneratedProtocolMessageType('FunctionStatus', (_message.Message,), dict(

  ExceptionInformation = _reflection.GeneratedProtocolMessageType('ExceptionInformation', (_message.Message,), dict(
    DESCRIPTOR = _FUNCTIONSTATUS_EXCEPTIONINFORMATION,
    __module__ = 'InstanceCommunication_pb2'
    # @@protoc_insertion_point(class_scope:proto.FunctionStatus.ExceptionInformation)
    ))
  ,

  DeserializationExceptionsEntry = _reflection.GeneratedProtocolMessageType('DeserializationExceptionsEntry', (_message.Message,), dict(
    DESCRIPTOR = _FUNCTIONSTATUS_DESERIALIZATIONEXCEPTIONSENTRY,
    __module__ = 'InstanceCommunication_pb2'
    # @@protoc_insertion_point(class_scope:proto.FunctionStatus.DeserializationExceptionsEntry)
    ))
  ,
  DESCRIPTOR = _FUNCTIONSTATUS,
  __module__ = 'InstanceCommunication_pb2'
  # @@protoc_insertion_point(class_scope:proto.FunctionStatus)
  ))
_sym_db.RegisterMessage(FunctionStatus)
_sym_db.RegisterMessage(FunctionStatus.ExceptionInformation)
_sym_db.RegisterMessage(FunctionStatus.DeserializationExceptionsEntry)

FunctionStatusList = _reflection.GeneratedProtocolMessageType('FunctionStatusList', (_message.Message,), dict(
  DESCRIPTOR = _FUNCTIONSTATUSLIST,
  __module__ = 'InstanceCommunication_pb2'
  # @@protoc_insertion_point(class_scope:proto.FunctionStatusList)
  ))
_sym_db.RegisterMessage(FunctionStatusList)

MetricsData = _reflection.GeneratedProtocolMessageType('MetricsData', (_message.Message,), dict(

  DataDigest = _reflection.GeneratedProtocolMessageType('DataDigest', (_message.Message,), dict(
    DESCRIPTOR = _METRICSDATA_DATADIGEST,
    __module__ = 'InstanceCommunication_pb2'
    # @@protoc_insertion_point(class_scope:proto.MetricsData.DataDigest)
    ))
  ,

  MetricsEntry = _reflection.GeneratedProtocolMessageType('MetricsEntry', (_message.Message,), dict(
    DESCRIPTOR = _METRICSDATA_METRICSENTRY,
    __module__ = 'InstanceCommunication_pb2'
    # @@protoc_insertion_point(class_scope:proto.MetricsData.MetricsEntry)
    ))
  ,
  DESCRIPTOR = _METRICSDATA,
  __module__ = 'InstanceCommunication_pb2'
  # @@protoc_insertion_point(class_scope:proto.MetricsData)
  ))
_sym_db.RegisterMessage(MetricsData)
_sym_db.RegisterMessage(MetricsData.DataDigest)
_sym_db.RegisterMessage(MetricsData.MetricsEntry)

HealthCheckResult = _reflection.GeneratedProtocolMessageType('HealthCheckResult', (_message.Message,), dict(
  DESCRIPTOR = _HEALTHCHECKRESULT,
  __module__ = 'InstanceCommunication_pb2'
  # @@protoc_insertion_point(class_scope:proto.HealthCheckResult)
  ))
_sym_db.RegisterMessage(HealthCheckResult)


DESCRIPTOR.has_options = True
DESCRIPTOR._options = _descriptor._ParseOptions(descriptor_pb2.FileOptions(), _b('\n!org.apache.pulsar.functions.protoB\025InstanceCommunication'))
_FUNCTIONSTATUS_DESERIALIZATIONEXCEPTIONSENTRY.has_options = True
_FUNCTIONSTATUS_DESERIALIZATIONEXCEPTIONSENTRY._options = _descriptor._ParseOptions(descriptor_pb2.MessageOptions(), _b('8\001'))
_METRICSDATA_METRICSENTRY.has_options = True
_METRICSDATA_METRICSENTRY._options = _descriptor._ParseOptions(descriptor_pb2.MessageOptions(), _b('8\001'))

_INSTANCECONTROL = _descriptor.ServiceDescriptor(
  name='InstanceControl',
  full_name='proto.InstanceControl',
  file=DESCRIPTOR,
  index=0,
  options=None,
  serialized_start=1105,
  serialized_end=1453,
  methods=[
  _descriptor.MethodDescriptor(
    name='GetFunctionStatus',
    full_name='proto.InstanceControl.GetFunctionStatus',
    index=0,
    containing_service=None,
    input_type=google_dot_protobuf_dot_empty__pb2._EMPTY,
    output_type=_FUNCTIONSTATUS,
    options=None,
  ),
  _descriptor.MethodDescriptor(
    name='GetAndResetMetrics',
    full_name='proto.InstanceControl.GetAndResetMetrics',
    index=1,
    containing_service=None,
    input_type=google_dot_protobuf_dot_empty__pb2._EMPTY,
    output_type=_METRICSDATA,
    options=None,
  ),
  _descriptor.MethodDescriptor(
    name='ResetMetrics',
    full_name='proto.InstanceControl.ResetMetrics',
    index=2,
    containing_service=None,
    input_type=google_dot_protobuf_dot_empty__pb2._EMPTY,
    output_type=google_dot_protobuf_dot_empty__pb2._EMPTY,
    options=None,
  ),
  _descriptor.MethodDescriptor(
    name='GetMetrics',
    full_name='proto.InstanceControl.GetMetrics',
    index=3,
    containing_service=None,
    input_type=google_dot_protobuf_dot_empty__pb2._EMPTY,
    output_type=_METRICSDATA,
    options=None,
  ),
  _descriptor.MethodDescriptor(
    name='HealthCheck',
    full_name='proto.InstanceControl.HealthCheck',
    index=4,
    containing_service=None,
    input_type=google_dot_protobuf_dot_empty__pb2._EMPTY,
    output_type=_HEALTHCHECKRESULT,
    options=None,
  ),
])
_sym_db.RegisterServiceDescriptor(_INSTANCECONTROL)

DESCRIPTOR.services_by_name['InstanceControl'] = _INSTANCECONTROL

# @@protoc_insertion_point(module_scope)
