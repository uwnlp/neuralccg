#include "decoder.h"

#include <fcntl.h>

#include "cnn/cnn.h"
#include "model/rnn-parse-scorer.h"
#include "util/delimited-proto-reader.h"
#include <google/protobuf/text_format.h>

using namespace cnn;
using namespace neuralccg;

namespace pb = google::protobuf;

std::unique_ptr<ParseScorer> scorer;
std::unique_ptr<Trainer> trainer;
std::unique_ptr<ScorerConfig> scorer_config;

template<typename Message>
void jbytes_to_message(jbyteArray buffer, Message *message, JNIEnv *env) {
  jbyte *buffer_elements = env->GetByteArrayElements(buffer, 0);
  int buffer_length = env->GetArrayLength(buffer);
  if (!message->ParseFromArray(reinterpret_cast<void *>(buffer_elements), buffer_length)) {
    throw std::runtime_error("Failed to parse protobuffer.");
  }
  env->ReleaseByteArrayElements(buffer, buffer_elements, JNI_ABORT);
}

jbyteArray message_to_jbytes(const pb::MessageLite &message, JNIEnv *env) {
  std::string buffer;
  if (!message.SerializeToString(&buffer)) {
    throw std::runtime_error("Failed to encode protobuffer.");
  }
  jbyteArray bytes = env->NewByteArray(buffer.size());
  env->SetByteArrayRegion(bytes, 0, buffer.size(), (jbyte*) buffer.data());
  return bytes;
}

void InitializeCNN(const RunConfig &run_config) {
  int argc = 3;
  char arg0[] = "--cnn-mem";
  char arg1[64];
  snprintf(arg1, 64, "%d", run_config.memory());
  char *args[] = { nullptr, arg0, arg1 };
  char **argv = static_cast<char**>(args);
  Initialize(argc, argv, run_config.seed());
}

void InitializeScorer(const ScorerConfig &scorer_config) {
  scorer.reset();
  ps->free();
  scorer.reset(new RnnParseScorer(scorer_config));

  if (scorer_config.has_model()) {
    std::cerr << "Loading existing model." << std::endl;
    scorer->Load(scorer_config.model());
  }
}

void InitializeTraining(const TrainConfig &train_config) {
  scorer->InitializeTraining(train_config);
  if (!train_config.update_method().empty()) {
    if (train_config.update_method() == "adam") {
      trainer.reset(new AdamTrainer(&scorer->model));
    }
    else if (train_config.update_method() == "momentum") {
      trainer.reset(new MomentumSGDTrainer(&scorer->model));
    } else {
      throw std::runtime_error("Unknown update method: " + train_config.update_method());
    }
  }
}

JNIEXPORT void JNICALL Java_edu_uw_neuralccg_model_TreeFactoredModel_initializeCNN
(JNIEnv *env, jclass clazz, jbyteArray run_config_buffer) {
  RunConfig run_config;
  jbytes_to_message(run_config_buffer, &run_config, env);
  InitializeCNN(run_config);
}

JNIEXPORT void JNICALL Java_edu_uw_neuralccg_model_TreeFactoredModel_initializeScorer___3B
(JNIEnv *env, jclass clazz, jbyteArray scorer_config_buffer) {
  scorer_config.reset(new ScorerConfig());
  jbytes_to_message(scorer_config_buffer, scorer_config.get(), env);
  InitializeScorer(*scorer_config);
}

JNIEXPORT void JNICALL Java_edu_uw_neuralccg_model_TreeFactoredModel_initializeScorer__Ljava_lang_String_2
(JNIEnv *env, jclass clazz, jstring checkpoint_path) {
  scorer_config.reset(new ScorerConfig());
  const char* checkpoint_path_cstr = env->GetStringUTFChars(checkpoint_path, nullptr);
  int fd = open(checkpoint_path_cstr, O_RDONLY);
  FileInputStream raw_in_stream(fd);
  CodedInputStream in_stream(&raw_in_stream);
  in_stream.SetTotalBytesLimit(64 << 23, 64 << 23); // 512 Mb.
  if (!scorer_config->ParseFromCodedStream(&in_stream)) {
    throw std::runtime_error("Failed to parse model protobuffer.");
  }
  close(fd);
  std::cerr << "Loaded model from " << checkpoint_path_cstr << std::endl;
  env->ReleaseStringUTFChars(checkpoint_path, checkpoint_path_cstr);
  InitializeScorer(*scorer_config);
}

JNIEXPORT void JNICALL Java_edu_uw_neuralccg_model_TreeFactoredModel_initializeTrainer
(JNIEnv *env, jclass clazz, jbyteArray train_config_buffer) {
  TrainConfig train_config;
  jbytes_to_message(train_config_buffer, &train_config, env);
  InitializeTraining(train_config);
}

JNIEXPORT void JNICALL Java_edu_uw_neuralccg_model_TreeFactoredModel_saveCheckpoint
(JNIEnv *env, jclass clazz, jstring checkpoint_path) {
  scorer_config->mutable_model()->Clear();
  scorer->Save(scorer_config->mutable_model());
  const char* checkpoint_path_cstr = env->GetStringUTFChars(checkpoint_path, nullptr);
  std::fstream out_stream(checkpoint_path_cstr, std::ios::out | std::ios::trunc | std::ios::binary);
  if(!scorer_config->SerializeToOstream(&out_stream)) {
    throw std::runtime_error("Failed to encode model protobuffer.");
  }
  out_stream.close();
  std::cerr << "Saved model to " << checkpoint_path_cstr << std::endl;
  env->ReleaseStringUTFChars(checkpoint_path, checkpoint_path_cstr);
}

JNIEXPORT void JNICALL Java_edu_uw_neuralccg_model_TreeFactoredModel_initializeSentence
    (JNIEnv *env, jclass clazz, jbyteArray buffer) {
  SentenceProto sentence;
  jbytes_to_message(buffer, &sentence, env);
  scorer->InitializeSentence(sentence, nullptr);
}

JNIEXPORT jbyteArray JNICALL Java_edu_uw_neuralccg_model_TreeFactoredModel_initializeSentenceWithGates
    (JNIEnv *env, jclass clazz, jbyteArray buffer) {
  SentenceProto sentence;
  jbytes_to_message(buffer, &sentence, env);
  InitialGatesProto initial_gates;
  scorer->InitializeSentence(sentence, &initial_gates);
  return message_to_jbytes(initial_gates, env);
}

JNIEXPORT jfloat JNICALL Java_edu_uw_neuralccg_model_TreeFactoredModel_scoreAndBuildRepresentation
    (JNIEnv *env, jclass clazz, jbyteArray buffer) {
  ParseProto parse;
  jbytes_to_message(buffer, &parse, env);
  return scorer->ScoreAndBuildRepresentation(parse, nullptr);
}

JNIEXPORT jbyteArray JNICALL Java_edu_uw_neuralccg_model_TreeFactoredModel_scoreAndBuildRepresentationWithGates
(JNIEnv *env, jclass clazz, jbyteArray buffer) {
  ParseProto parse;
  jbytes_to_message(buffer, &parse, env);
  GatesProto gates;
  float score = scorer->ScoreAndBuildRepresentation(parse, &gates);
  gates.set_score(score);
  return message_to_jbytes(gates, env);
}

JNIEXPORT void JNICALL Java_edu_uw_neuralccg_model_TreeFactoredModel_applyUpdate
(JNIEnv *env, jclass clazz, jbyteArray buffer) {
  UpdateProto update;
  jbytes_to_message(buffer, &update, env);
  scorer->ApplyUpdate(update, trainer.get());
}
