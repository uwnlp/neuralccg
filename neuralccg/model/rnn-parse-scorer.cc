#include "neuralccg/proto/train.pb.h"
#include "rnn-parse-scorer.h"
#include "util/serialization-util.h"

using namespace cnn;

namespace neuralccg {

void RnnParseScorer::CategoryToString(const CategoryProto& category, std::string *result) {
  if (category.has_left()) {
    result->append("(");
    CategoryToString(category.left(), result);
    switch (category.slash()) {
    case FWD:
      result->append("/");
      break;
    case BWD:
      result->append("\\");
      break;
    default:
      result->append("|");
      break;
    }
    CategoryToString(category.right(), result);
    result->append(")");
  } else {
    result->append(category.atomic());
  }
}

RnnParseScorer::RnnParseScorer(const ScorerConfig &config) : config(config) {
  // Setup and freeze feature dictionaries.
  for (const std::string &word : config.word()) {
    word_dictionary.Convert(word);
  }
  word_start = word_dictionary.Convert("<s>");
  word_end = word_dictionary.Convert("</s>");
  word_dictionary.Freeze();
  word_dictionary.SetUnk("*UNKNOWN*");

  if (config.use_char_lstm()) {
    for (const std::string &word : config.word()) {
      for (const char &c : word) {
        char_dictionary.Convert(std::string(1, c));
      }
    }
    char_start = char_dictionary.Convert("<c>");
    char_end = char_dictionary.Convert("</c>");
    char_dictionary.Convert("*UNKNOWN*");
    char_dictionary.Freeze();
    char_dictionary.SetUnk("*UNKNOWN*");
    std::cerr << char_dictionary.size() << " characters found." << std::endl;
  }

  // Setup and freeze category dictionary.
  for (const CategoryProto &category : config.category()) {
    std::string category_string;
    CategoryToString(category, &category_string);
    category_dictionary.Convert(category_string);
    if (config.use_compositional_categories() && category.has_left()) {
      throw std::invalid_argument("Only atomic categories should be given if using compositional categories. Found: " + category_string);
    }
  }
  category_dictionary.Freeze();
  category_dictionary.SetUnk("*UNKNOWN*");

  // Negative layer number means we are ablating lexical inputs.
  if (config.num_layers() >= 0) {
    word_params = model.add_lookup_parameters(word_dictionary.size(), {config.word_dimensions()});

    if (config.num_layers() > 0) {
      forward_lstm.reset(new CustomLSTMBuilder(config.num_layers(), config.word_dimensions(), config.cell_dimensions(), &model));
      backward_lstm.reset(new CustomLSTMBuilder(config.num_layers(), config.word_dimensions(), config.cell_dimensions(), &model));
      if (config.use_char_lstm()) {
        char_forward_lstm.reset(new LSTMBuilder(config.num_layers(), config.word_dimensions()/2, config.word_dimensions()/2, &model));
        char_backward_lstm.reset(new LSTMBuilder(config.num_layers(), config.word_dimensions()/2, config.word_dimensions()/2, &model));
      }
    }
  }

  /*
   * 3 params.
   */
  params.resize(3);
  params_cg.resize(params.size());

  // Null embeddings for unary rules.
  params[NC] = model.add_parameters({config.cell_dimensions()});
  params[NH] = model.add_parameters({config.cell_dimensions()});

  // Parse score params.
  params[PW] = model.add_parameters({config.cell_dimensions()});

  /*
   * 14 indexed params.
   */
  indexed_params.resize(14);
  indexed_params_cg.resize(indexed_params.size());

  // Character.
  indexed_params[CHE].resize(char_dictionary.size());
  for (unsigned i = 0; i < char_dictionary.size(); ++i) {
    indexed_params[CHE][i] = model.add_parameters({config.word_dimensions()/2});
  }

  // Category.
  indexed_params[CE].resize(category_dictionary.size());
  for (unsigned i = 0; i < category_dictionary.size(); ++i) {
    indexed_params[CE][i] = model.add_parameters({config.category_dimensions()});
  }

  // Slash.
  indexed_params[SW].resize(SlashProto_ARRAYSIZE);
  indexed_params[SB].resize(SlashProto_ARRAYSIZE);
  for (int i = 0; i < SlashProto_ARRAYSIZE; ++i) {
    indexed_params[SW][i] = model.add_parameters({config.category_dimensions(), 2 * config.category_dimensions()});
    indexed_params[SB][i] = model.add_parameters({config.category_dimensions()});
  }

  // Input gate.
  indexed_params[IW].resize(RuleTypeProto_ARRAYSIZE);
  indexed_params[IB].resize(RuleTypeProto_ARRAYSIZE);
  for (unsigned i = 0; i < RuleTypeProto_ARRAYSIZE; ++i) {
    indexed_params[IW][i] = model.add_parameters({config.cell_dimensions(), config.category_dimensions() + 4 * config.cell_dimensions()});
    indexed_params[IB][i] = model.add_parameters({config.cell_dimensions()});
  }

  // Left Forget gate.
  indexed_params[LFW].resize(RuleTypeProto_ARRAYSIZE);
  indexed_params[LFB].resize(RuleTypeProto_ARRAYSIZE);
  for (unsigned i = 0; i < RuleTypeProto_ARRAYSIZE; ++i) {
    indexed_params[LFW][i] = model.add_parameters({config.cell_dimensions(), config.category_dimensions() + 4 * config.cell_dimensions()});
    indexed_params[LFB][i] = model.add_parameters({config.cell_dimensions()});
  }

  // Right Forget gate.
  indexed_params[RFW].resize(RuleTypeProto_ARRAYSIZE);
  indexed_params[RFB].resize(RuleTypeProto_ARRAYSIZE);
  for (unsigned i = 0; i < RuleTypeProto_ARRAYSIZE; ++i) {
    indexed_params[RFW][i] = model.add_parameters({config.cell_dimensions(), config.category_dimensions() + 4 * config.cell_dimensions()});
    indexed_params[RFB][i] = model.add_parameters({config.cell_dimensions()});
  }

  // Cell.
  indexed_params[CW].resize(RuleTypeProto_ARRAYSIZE);
  indexed_params[CB].resize(RuleTypeProto_ARRAYSIZE);
  for (unsigned i = 0; i < RuleTypeProto_ARRAYSIZE; ++i) {
    // Intermediate cell only looks at the children's cells.
    indexed_params[CW][i] = model.add_parameters({config.cell_dimensions(), config.category_dimensions() + 2 * config.cell_dimensions()});
    indexed_params[CB][i] = model.add_parameters({config.cell_dimensions()});
  }

  // Output gate.
  indexed_params[OW].resize(RuleTypeProto_ARRAYSIZE);
  indexed_params[OB].resize(RuleTypeProto_ARRAYSIZE);
  for (unsigned i = 0; i < RuleTypeProto_ARRAYSIZE; ++i) {
    // Output gate only looks at the current cell and children's outputs.
    indexed_params[OW][i] = model.add_parameters({config.cell_dimensions(), config.category_dimensions() + 3 * config.cell_dimensions()});
    indexed_params[OB][i] = model.add_parameters({config.cell_dimensions()});
  }

  for (unsigned i = 0; i < indexed_params.size(); ++i) {
    indexed_params_cg[i].resize(indexed_params[i].size());
  }
}

void RnnParseScorer::InitializeTraining(const TrainConfig &train_config) {
  if (config.num_layers() >= 0) {
    std::vector<float> values;
    values.reserve(train_config.initial_embedding(0).value_size());
    for (const WordEmbedding &embedding : train_config.initial_embedding()) {
      values.clear();
      for (float value : embedding.value()) {
        values.push_back(value);
      }
      TensorTools::SetElements(word_params->values[word_dictionary.Convert(embedding.word())], values);
    }
  }
}

expr::Expression RnnParseScorer::BuildInputEmbedding(const std::string &word) {
  if (config.use_char_lstm()) {
      char_forward_lstm->start_new_sequence();
      char_backward_lstm->start_new_sequence();
      char_forward_lstm->add_input(indexed_params_cg[CHE][char_start]);
      char_backward_lstm->add_input(indexed_params_cg[CHE][char_end]);
      expr::Expression forward_output, backward_output;
      for (unsigned i = 0; i < word.size(); ++i) {
        forward_output = char_forward_lstm->add_input(indexed_params_cg[CHE][char_dictionary.Convert(std::string(1, word.at(i)))]);
        backward_output = char_backward_lstm->add_input(indexed_params_cg[CHE][char_dictionary.Convert(std::string(1, word.at(word.size() - i - 1)))]);
      }
      return expr::concatenate({forward_output, backward_output});
  } else {
    return expr::lookup(*cg, word_params, word_dictionary.Convert(word));
  }
}

expr::Expression RnnParseScorer::BuildStartInputEmbedding() {
  return expr::lookup(*cg, word_params, word_start);
}

expr::Expression RnnParseScorer::BuildEndInputEmbedding() {
  return expr::lookup(*cg, word_params, word_end);
}

void RnnParseScorer::InitializeSentence(const SentenceProto &sentence, InitialGatesProto *initial_gates) {
  cg.reset();
  cg.reset(new ComputationGraph());
  cells.clear();
  outputs.clear();
  accumulated_scores.clear();

  for (unsigned i = 0; i < params.size(); ++i) {
    params_cg[i] = expr::parameter(*cg, params[i]);
  }

  for (unsigned i = 0; i < indexed_params.size(); ++i) {
    for (unsigned j = 0; j < indexed_params[i].size(); ++j) {
      indexed_params_cg[i][j] = expr::parameter(*cg, indexed_params[i][j]);
    }
  }

  if (config.num_layers() >= 0) {
    input_embeddings.clear();
    input_embeddings.reserve(sentence.word_size());

    if (config.use_char_lstm()) {
      char_forward_lstm->new_graph(*cg);
      char_backward_lstm->new_graph(*cg);
    }
    for (const std::string &word : sentence.word()) {
      expr::Expression input_embedding = BuildInputEmbedding(word);
      if (!sentence.is_eval()) {
        input_embedding = expr::dropout(input_embedding, config.dropout_probability());
      }
      input_embeddings.push_back(input_embedding);
    }
    if (config.num_layers() > 0) {
      forward_lstm->new_graph(*cg);
      forward_lstm->start_new_sequence();
      backward_lstm->new_graph(*cg);
      backward_lstm->start_new_sequence();

      forward_lstm->add_input(BuildStartInputEmbedding());
      backward_lstm->add_input(BuildEndInputEmbedding());

      for (unsigned i = 0; i < input_embeddings.size(); ++i) {
        forward_lstm->add_input(input_embeddings[i]);
        backward_lstm->add_input(input_embeddings[input_embeddings.size() - i - 1]);
      }

      if (initial_gates) {
        for (unsigned i = 0; i < input_embeddings.size(); ++i) {
          GatesProto *forward_gates = initial_gates->add_forward_gates();
          SerializationUtil::AsTensorProto(cg->get_value(forward_lstm->input_gates.at(i + 1)), forward_gates->mutable_input_gate());
          SerializationUtil::AsTensorProto(cg->get_value(forward_lstm->forget_gates.at(i + 1)), forward_gates->mutable_left_forget_gate());

          GatesProto *backward_gates = initial_gates->add_backward_gates();
          SerializationUtil::AsTensorProto(cg->get_value(backward_lstm->input_gates.at(input_embeddings.size() - i - 1)), backward_gates->mutable_input_gate());
          SerializationUtil::AsTensorProto(cg->get_value(backward_lstm->forget_gates.at(input_embeddings.size() - i - 1)), backward_gates->mutable_left_forget_gate());
        }
      }
    }
  }
  zero = expr::zeroes(*cg, {1});
  zero_cell_embedding = expr::zeroes(*cg, {config.cell_dimensions()});
  zero_category_embedding = expr::zeroes(*cg, {config.category_dimensions()});
}

expr::Expression RnnParseScorer::BuildCategoryEmbedding(const CategoryProto& category) {
  if (config.use_compositional_categories()) {
    if (category.has_left()) {
      expr::Expression left_embedding = BuildCategoryEmbedding(category.left());
      expr::Expression right_embedding = BuildCategoryEmbedding(category.right());
      return expr::tanh(expr::affine_transform({indexed_params_cg[SB][category.slash()],
                                                indexed_params_cg[SW][category.slash()],
                                                expr::concatenate({left_embedding, right_embedding})}));
    } else {
      return indexed_params_cg[CE][category_dictionary.Convert(category.atomic())];
    }
  } else {
    std::string category_string;
    CategoryToString(category, &category_string);
    return indexed_params_cg[CE][category_dictionary.Convert(category_string)];
  }
}

expr::Expression RnnParseScorer::ApplyRule(const RuleTypeProto &rule_type,
                                           int weights,
                                           int bias,
                                           expr::Expression concat_embedding) {
  return expr::affine_transform({indexed_params_cg[bias][rule_type],
                                 indexed_params_cg[weights][rule_type],
                                 concat_embedding});
}

float RnnParseScorer::ScoreAndBuildRepresentation(const ParseProto &parse, GatesProto *gates) {
  expr::Expression category_embedding;
  if (config.use_nonterminal_categories() || parse.child_size() == 0) {
    category_embedding = BuildCategoryEmbedding(parse.category());
  } else {
    category_embedding = zero_category_embedding;
  }

  expr::Expression left_cell, left_output, right_cell, right_output;
  if (config.use_recursion()) {
    switch (parse.child_size()) {
      case 0: {
        if (parse.start() != parse.end()) {
          throw std::invalid_argument("Leaves should have the same start and end indices. Got "
             + std::to_string(parse.start()) + " and "
             + std::to_string(parse.end()));
        }
        if (config.num_layers() > 0) {
          left_cell = forward_lstm->c.at(parse.start() + 1).back();
          left_output = forward_lstm->h.at(parse.start() + 1).back();
          right_cell = backward_lstm->c.at(backward_lstm->c.size() - 1 - parse.start()).back();
          right_output = backward_lstm->h.at(backward_lstm->h.size() - 1 - parse.start()).back();
        } else if (config.num_layers() == 0) {
          // Ablate LSTMs.
          left_cell = zero_cell_embedding;
          left_output = zero_cell_embedding;
          right_cell = expr::concatenate({input_embeddings.at(parse.start()), expr::zeroes(*cg, {config.cell_dimensions() - config.word_dimensions()})});
          right_output = zero_cell_embedding;
        }
        else {
          // Ablate lexical inputs.
          left_cell = zero_cell_embedding;
          left_output = zero_cell_embedding;
          right_cell = zero_cell_embedding;
          right_output = zero_cell_embedding;
        }
        break;
      }
      case 1: {
        left_cell = params_cg[NC];
        left_output = params_cg[NH];
        right_cell = cells.at(parse.child(0));
        right_output = outputs.at(parse.child(0));
        break;
      }
      case 2: {
        left_cell = cells.at(parse.child(0));
        left_output = outputs.at(parse.child(0));
        right_cell = cells.at(parse.child(1));
        right_output = outputs.at(parse.child(1));
        break;
      }
      default: {
        throw std::invalid_argument("Unsupported number of children: " + std::to_string(parse.child_size()));
      }
    }
  } else {
     left_cell = forward_lstm->c.at(parse.end() + 1).back();
     left_output = forward_lstm->h.at(parse.end() + 1).back();
     right_cell = backward_lstm->c.at(backward_lstm->c.size() - 1 - parse.start()).back();
     right_output = backward_lstm->h.at(backward_lstm->h.size() - 1 - parse.start()).back();
  }

  expr::Expression children_score;
  switch (parse.child_size()) {
    case 0:
      children_score = zero;
      break;
    case 1:
      children_score = accumulated_scores.at(parse.child(0));
      break;
    case 2:
      children_score = accumulated_scores.at(parse.child(0)) + accumulated_scores.at(parse.child(1));
      break;
  }

  expr::Expression embedding_for_intermediate_cell = expr::concatenate({category_embedding, left_output, right_output});
  expr::Expression intermediate_cell = expr::tanh(ApplyRule(parse.rule_type(), CW, CB, embedding_for_intermediate_cell));

  expr::Expression embedding_for_gates = expr::concatenate({category_embedding, left_cell, left_output, right_cell, right_output});
  expr::Expression input_gate, left_intermediate_forget_gate, right_intermediate_forget_gate;
  input_gate = expr::logistic(ApplyRule(parse.rule_type(), IW, IB, embedding_for_gates));
  if (config.couple_gates()) {
    left_intermediate_forget_gate = expr::logistic(ApplyRule(parse.rule_type(), LFW, LFB, embedding_for_gates));
    right_intermediate_forget_gate = 1.0f - left_intermediate_forget_gate;
  } else {
    left_intermediate_forget_gate = expr::logistic(ApplyRule(parse.rule_type(), LFW, LFB, embedding_for_gates) + 1.0f);
    right_intermediate_forget_gate = expr::logistic(ApplyRule(parse.rule_type(), RFW, RFB, embedding_for_gates) + 1.0f);
  }

  expr::Expression left_forget_gate, right_forget_gate;
  if (config.couple_gates()) {
    expr::Expression children_forget_gate = 1.0f - input_gate;
    left_forget_gate = expr::cwise_multiply(children_forget_gate, left_intermediate_forget_gate);
    right_forget_gate = expr::cwise_multiply(children_forget_gate, right_intermediate_forget_gate);
  } else {
    left_forget_gate = left_intermediate_forget_gate;
    right_forget_gate = right_intermediate_forget_gate;
  }

  if (gates) {
    SerializationUtil::AsTensorProto(cg->get_value(input_gate), gates->mutable_input_gate());
    SerializationUtil::AsTensorProto(cg->get_value(left_forget_gate), gates->mutable_left_forget_gate());
    SerializationUtil::AsTensorProto(cg->get_value(right_forget_gate), gates->mutable_right_forget_gate());
  }

  expr::Expression current_cell =
    expr::cwise_multiply(input_gate, intermediate_cell) +
    expr::cwise_multiply(left_forget_gate, left_cell) +
    expr::cwise_multiply(right_forget_gate, right_cell);

  expr::Expression current_output = expr::tanh(current_cell);
  if (config.use_output_gate()) {
    expr::Expression embedding_for_output_gate = expr::concatenate({category_embedding, current_cell, left_output, right_output});
    expr::Expression output_gate = expr::logistic(ApplyRule(parse.rule_type(), OW, OB, embedding_for_output_gate));
    current_output = expr::cwise_multiply(output_gate, current_output);
  }

  expr::Expression current_score;
  if (config.score_supertags() || parse.child_size() > 0) {
    expr::Expression raw_score = expr::dot_product(params_cg[PW], current_output);
    current_score = -expr::log(1 + expr::exp(raw_score));
  } else {
    current_score = zero;
  }
  cells.push_back(current_cell);
  outputs.push_back(current_output);
  accumulated_scores.push_back(current_score + children_score);
  return as_scalar(cg->get_value(current_score));
}

void RnnParseScorer::ApplyUpdate(const UpdateProto &update, Trainer *trainer) {
  expr::Expression loss_expression;
  if (update.use_crf_loss()) {
      std::vector<expr::Expression> all, correct;
      for (const int index : update.incorrect()) {
        all.push_back(accumulated_scores.at(index));
      }
      for (const int index : update.correct()) {
        all.push_back(accumulated_scores.at(index));
        correct.push_back(accumulated_scores.at(index));
      }
      loss_expression = expr::logsumexp(all) - expr::logsumexp(correct);
  } else {
    if (update.incorrect_size() == 1 && update.correct_size() == 1) {
      loss_expression = accumulated_scores.at(update.incorrect(0)) - accumulated_scores.at(update.correct(0));
    } else {
      std::vector<expr::Expression> incorrect, correct;
      for (const int index : update.incorrect()) {
        incorrect.push_back(accumulated_scores.at(index));
      }
      for (const int index : update.correct()) {
        correct.push_back(accumulated_scores.at(index));
      }
      loss_expression = expr::sum(incorrect) - expr::sum(correct);
    }
  }
  as_scalar(cg->get_value(loss_expression));
  cg->backward();
  trainer->update(1.0);
}

}
