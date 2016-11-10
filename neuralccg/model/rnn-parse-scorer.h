#ifndef NEURALCCG_MODEL_RNN_PARSE_SCORER_H_
#define NEURALCCG_MODEL_RNN_PARSE_SCORER_H_

#include "cnn/cnn.h"
#include "cnn/nodes.h"
#include "cnn/dict.h"
#include "cnn/expr.h"
#include "cnn/lstm.h"
#include "parse-scorer.h"
#include "custom-lstm.h"

using namespace cnn;

namespace neuralccg {

class RnnParseScorer: public ParseScorer {
 public:
  RnnParseScorer(const ScorerConfig &config);
  void InitializeTraining(const TrainConfig &train_config) override;
  void InitializeSentence(const SentenceProto &sentence, InitialGatesProto *initial_gates) override;
  float ScoreAndBuildRepresentation(const ParseProto &parse, GatesProto *gates) override;
  void ApplyUpdate(const UpdateProto &update, Trainer *trainer) override;

  // See rnn-parse-scorer.cc for explanations of the parameters.
  enum { CHE, CE, SW, SB, CW, CB, IW, IB, LFW, LFB, RFW, RFB, OW, OB };
  enum { NC, NH, PW };

 private:
  expr::Expression BuildCategoryEmbedding(const CategoryProto& category);
  expr::Expression BuildInputEmbedding(const std::string &word);
  expr::Expression BuildStartInputEmbedding();
  expr::Expression BuildEndInputEmbedding();
  expr::Expression ApplyRule(const RuleTypeProto &rule_type,
                             int weights,
                             int bias,
                             expr::Expression concat_embedding);
  unsigned ConvertPrefix(const std::string& word, unsigned index);
  unsigned ConvertSuffix(const std::string& word, unsigned index);
  void CategoryToString(const CategoryProto& category, std::string *result);

  const ScorerConfig &config;

  std::vector<Parameters *> params;
  std::vector<std::vector<Parameters *>> indexed_params;
  LookupParameters * word_params;

  std::unique_ptr<ComputationGraph> cg;
  std::vector<expr::Expression> input_embeddings;
  std::unique_ptr<CustomLSTMBuilder> forward_lstm;
  std::unique_ptr<CustomLSTMBuilder> backward_lstm;
  std::unique_ptr<LSTMBuilder> char_forward_lstm;
  std::unique_ptr<LSTMBuilder> char_backward_lstm;

  std::vector<expr::Expression> params_cg;
  std::vector<std::vector<expr::Expression>> indexed_params_cg;
  std::vector<expr::Expression> cells;
  std::vector<expr::Expression> outputs;
  std::vector<expr::Expression> accumulated_scores;
  expr::Expression zero;
  expr::Expression zero_cell_embedding;
  expr::Expression zero_category_embedding;

  Dict category_dictionary;

  Dict word_dictionary;
  unsigned word_start, word_end;

  Dict char_dictionary;
  unsigned char_start, char_end;
};
}

#endif
