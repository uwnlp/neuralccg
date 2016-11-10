#/bin/bash

# Fail if we reference any unbound environment variables.
set -u

# Check if external data needs to be downloaded.
resource_url=lil.cs.washington.edu/resources
data_dir=neuralccg/data
if [ ! -e $data_dir ]
then
    echo "Downloading data from $resource_url"
    mkdir $data_dir

    # Turian et al. (2010) 50-dimensional word embeddings.
    curl -o $data_dir/embeddings.raw $resource_url/embeddings.raw

    # Lewis et al. (2016) supertagging model.
    supertagger=model_tritrain_finetune_long.tgz
    curl -o $supertagger $resource_url/$supertagger
    tar -xzvf $supertagger -C $data_dir
    rm $supertagger

    # Lee et al. (2016) parsing model.
    curl -o $data_dir/llz2016.model.pb $resource_url/llz2016.model.pb
else
    echo "Using cached data from $data_dir"
fi

lib_dir=neuralccg/lib
if [ ! -e $lib_dir ]
then
    echo "Downloading binaries from $resource_url"
    mkdir $lib_dir
    curl -o $lib_dir/libtaggerflow.so $resource_url/libtaggerflow.so
    curl -o $lib_dir/libtaggerflow.jnilib $resource_url/libtaggerflow.jnilib
else
    echo "Using cached binaries from $lib_dir"
fi

rm -f jni/java_home
ln -sf $JAVA_HOME jni/java_home

# Build JNI binaries and move them to the appropriate location.
bazel build -c opt neuralccg:libdecoder.so

rm -f $lib_dir/libdecoder.so
cp bazel-bin/neuralccg/libdecoder.so $lib_dir/libdecoder.so

rm -f $lib_dir/libdecoder.jnilib
cp bazel-bin/neuralccg/libdecoder.so $lib_dir/libdecoder.jnilib
