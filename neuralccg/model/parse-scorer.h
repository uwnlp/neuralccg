#ifndef NEURALCCG_MODEL_PARSE_SCORER_H
#define NEURALCCG_MODEL_PARSE_SCORER_H

#include "cnn/cnn.h"
#include "cnn/expr.h"
#include "cnn/training.h"
#include "neuralccg/proto/train.pb.h"
#include "neuralccg/proto/syntax.pb.h"

using namespace cnn;

namespace neuralccg {

class ParseScorer {
 public:
  ParseScorer() {};
  virtual ~ParseScorer() {};
  void Save(ModelProto *model_proto) const;
  void Save(const std::string &model_file) const;
  void Load(const ModelProto &model_proto);
  void Load(const std::string &model_file);

  virtual void InitializeTraining(const TrainConfig &train_config) = 0;
  virtual void InitializeSentence(const SentenceProto &chart, InitialGatesProto *initial_gates) = 0;
  virtual float ScoreAndBuildRepresentation(const ParseProto &parse, GatesProto *gates) = 0;
  virtual void ApplyUpdate(const UpdateProto &update, Trainer *trainer) = 0;

  cnn::Model model;
};

}

#endif
