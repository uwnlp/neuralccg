#ifndef UTIL_DELIMITED_PROTO_READER_H_
#define UTIL_DELIMITED_PROTO_READER_H_

#include <fcntl.h>
#include <fstream>
#include <google/protobuf/message_lite.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/io/coded_stream.h>

using namespace google::protobuf::io;
namespace neuralccg {

template<typename T>
class DelimitedProtoReader {
 public:
  DelimitedProtoReader(const std::string &path) {
    file_descriptor = open(path.c_str(), O_RDONLY);
    assert(file_descriptor >= 0);
    input_stream.reset(new FileInputStream(file_descriptor));
  }

  ~DelimitedProtoReader() {
    close(file_descriptor);
  }

  bool Read(T *message) {
    google::protobuf::io::CodedInputStream input(input_stream.get());
    uint32_t size;
    if (!input.ReadVarint32(&size)) {
      return false;
    }

    google::protobuf::io::CodedInputStream::Limit limit = input.PushLimit(size);

    if (!message->ParseFromCodedStream(&input)) {
      return false;
    }
    if (!input.ConsumedEntireMessage()) {
      return false;
    }

    input.PopLimit(limit);
    return true;
  }

  void Apply(const std::function<void(const T &)> &map_function) {
    T item;
    while (Read(&item)) {
      map_function(item);
    }
  }

 private:
  std::unique_ptr<ZeroCopyInputStream> input_stream;
  int file_descriptor;
};

}

#endif
