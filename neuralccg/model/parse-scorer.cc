#include "parse-scorer.h"

#include <fstream>
#include "util/serialization-util.h"

using namespace cnn;

namespace neuralccg {

void ParseScorer::Save(ModelProto *model_proto) const {
  SerializationUtil::AsModelProto(model, model_proto);
}

void ParseScorer::Save(const std::string &model_file) const {
  ModelProto model_proto;
  Save(&model_proto);
  std::fstream out_stream(model_file, std::ios::out | std::ios::trunc | std::ios::binary);
  if (!model_proto.SerializeToOstream(&out_stream)) {
    throw std::runtime_error("Failed to serialize model.");
  }
  out_stream.close();
  std::cerr << "Saved model to " << model_file << std::endl;
}

void ParseScorer::Load(const ModelProto &model_proto) {
  SerializationUtil::FromModelProto(model_proto, &model);
}

void ParseScorer::Load(const std::string &model_file) {
  ModelProto model_proto;
  std::fstream in_stream(model_file, std::ios::in | std::ios::binary);
  if (!model_proto.ParseFromIstream(&in_stream)) {
    throw std::runtime_error("Failed to load model.");
  }
  in_stream.close();
  Load(model_proto);
  std::cerr << "Loaded model from " << model_file << std::endl;
}

}
