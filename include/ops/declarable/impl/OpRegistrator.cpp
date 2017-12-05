//
// Created by raver119 on 07.10.2017.
//



#include <ops/declarable/OpRegistrator.h>
#include <sstream>

namespace nd4j {
    namespace ops {

        ///////////////////////////////

        template <typename OpName>
        __registratorFloat<OpName>::__registratorFloat() {
            OpName *ptr = new OpName();
            OpRegistrator::getInstance()->registerOperationFloat(ptr);
        }

        template <typename OpName>
        __registratorHalf<OpName>::__registratorHalf() {
            OpName *ptr = new OpName();
            OpRegistrator::getInstance()->registerOperationHalf(ptr);
        }

        template <typename OpName>
        __registratorDouble<OpName>::__registratorDouble() {
            OpName *ptr = new OpName();
            OpRegistrator::getInstance()->registerOperationDouble(ptr);
        }

        template <typename OpName>
        __registratorSynonymFloat<OpName>::__registratorSynonymFloat(const char *name, const char *oname) {
            OpName *ptr = (OpName *) OpRegistrator::getInstance()->getOperationFloat(oname);
            if (ptr == nullptr) {
                std::string newName(name);
                std::string oldName(oname);

                OpRegistrator::getInstance()->updateMSVC(nd4j::ops::HashHelper::getInstance()->getLongHash(newName), oldName);
                return;
            }
            OpRegistrator::getInstance()->registerOperationFloat(name, ptr);
        }

        template <typename OpName>
        __registratorSynonymHalf<OpName>::__registratorSynonymHalf(const char *name, const char *oname) {
            OpName *ptr = (OpName *) OpRegistrator::getInstance()->getOperationHalf(oname);
            if (ptr == nullptr) {
                std::string newName(name);
                std::string oldName(oname);

                OpRegistrator::getInstance()->updateMSVC(nd4j::ops::HashHelper::getInstance()->getLongHash(newName), oldName);
                return;
            }
            OpRegistrator::getInstance()->registerOperationHalf(name, ptr);
        }

        template <typename OpName>
        __registratorSynonymDouble<OpName>::__registratorSynonymDouble(const char *name, const char *oname) {
            OpName *ptr = (OpName *) OpRegistrator::getInstance()->getOperationDouble(oname);
            if (ptr == nullptr) {
                std::string newName(name);
                std::string oldName(oname);

                OpRegistrator::getInstance()->updateMSVC(nd4j::ops::HashHelper::getInstance()->getLongHash(newName), oldName);
                return;
            }
            OpRegistrator::getInstance()->registerOperationDouble(name, ptr);
        }

        ///////////////////////////////


        OpRegistrator* OpRegistrator::getInstance() {
            if (!_INSTANCE)
                _INSTANCE = new nd4j::ops::OpRegistrator();

            return _INSTANCE;
        }


        void OpRegistrator::updateMSVC(Nd4jIndex newHash, std::string& oldName) {
            std::pair<Nd4jIndex, std::string> pair(newHash, oldName);
            _msvc.insert(pair);
        }

        template <typename T>
        std::string OpRegistrator::local_to_string(T value) {
            //create an output string stream
            std::ostringstream os ;

            //throw the value into the string stream
            os << value ;

            //convert the string stream into a string and return
            return os.str() ;
        }

        OpRegistrator::~OpRegistrator() {
            _msvc.clear();

            for (auto const& x : _declarablesD)
                delete x.second;

            for (auto const& x : _declarablesF)
                delete x.second;

            for (auto const& x : _declarablesH)
                delete x.second;

            _declarablesF.clear();
            _declarablesD.clear();
            _declarablesH.clear();

            _declarablesLD.clear();
            _declarablesLF.clear();
            _declarablesLH.clear();
        }

        const char * OpRegistrator::getAllCustomOperations() {
            _locker.lock();

            if (!isInit) {
                for (std::map<std::string, nd4j::ops::DeclarableOp<float>*>::iterator it=_declarablesF.begin(); it!=_declarablesF.end(); ++it) {
                    std::string op = it->first + ":"
                                     + local_to_string(it->second->getOpDescriptor()->getHash()) + ":"
                                     + local_to_string(it->second->getOpDescriptor()->getNumberOfInputs()) + ":"
                                     + local_to_string(it->second->getOpDescriptor()->getNumberOfOutputs()) + ":"
                                     + local_to_string(it->second->getOpDescriptor()->allowsInplace())  + ":"
                                     + local_to_string(it->second->getOpDescriptor()->getNumberOfTArgs())  + ":"
                                     + local_to_string(it->second->getOpDescriptor()->getNumberOfIArgs())  + ":"
                                     + ";" ;
                    _opsList += op;
                }

                isInit = true;
            }

            _locker.unlock();

            return _opsList.c_str();
        }

        /**
         * This method registers operation
         *
         * @param op
         */
        bool OpRegistrator::registerOperationFloat(nd4j::ops::DeclarableOp<float>* op) {
            return registerOperationFloat(op->getOpName()->c_str(), op);
        }

        bool OpRegistrator::registerOperationFloat(const char* name, nd4j::ops::DeclarableOp<float>* op) {
            std::string str(name);
            std::pair<std::string, nd4j::ops::DeclarableOp<float>*> pair(str, op);
            _declarablesF.insert(pair);

            auto hash = nd4j::ops::HashHelper::getInstance()->getLongHash(str);
            std::pair<Nd4jIndex, nd4j::ops::DeclarableOp<float>*> pair2(hash, op);
            _declarablesLF.insert(pair2);

            //nd4j_printf("Adding op [%s]:[%lld]\n", name, hash);
            return true;
        }

        bool OpRegistrator::registerOperationDouble(const char* name, nd4j::ops::DeclarableOp<double>* op) {
            std::string str(name);
            std::pair<std::string, nd4j::ops::DeclarableOp<double>*> pair(str, op);
            _declarablesD.insert(pair);

            auto hash = nd4j::ops::HashHelper::getInstance()->getLongHash(str);
            std::pair<Nd4jIndex, nd4j::ops::DeclarableOp<double>*> pair2(hash, op);
            _declarablesLD.insert(pair2);
            return true;
        }

        bool OpRegistrator::registerOperationHalf(const char* name, nd4j::ops::DeclarableOp<float16>* op) {
            std::string str(name);
            std::pair<std::string, nd4j::ops::DeclarableOp<float16>*> pair(str, op);
            _declarablesH.insert(pair);

            auto hash = nd4j::ops::HashHelper::getInstance()->getLongHash(str);
            std::pair<Nd4jIndex, nd4j::ops::DeclarableOp<float16>*> pair2(hash, op);
            _declarablesLH.insert(pair2);
            return true;
        }

        bool OpRegistrator::registerOperationHalf(nd4j::ops::DeclarableOp<float16> *op) {
            return registerOperationHalf(op->getOpName()->c_str(), op);
        }

        bool OpRegistrator::registerOperationDouble(nd4j::ops::DeclarableOp<double> *op) {
            return registerOperationDouble(op->getOpName()->c_str(), op);
        }

        nd4j::ops::DeclarableOp<float>* OpRegistrator::getOperationFloat(const char *name) {
            std::string str(name);
            return getOperationFloat(str);
        }

        /**
         * This method returns registered Op by name
         *
         * @param name
         * @return
         */
        nd4j::ops::DeclarableOp<float>* OpRegistrator::getOperationFloat(std::string& name) {
            if (!_declarablesF.count(name)) {
                nd4j_debug("Unknown operation requested: [%s]\n", name.c_str());
                return nullptr;
            }

            return _declarablesF.at(name);
        }


        nd4j::ops::DeclarableOp<float> *OpRegistrator::getOperationFloat(Nd4jIndex hash) {
            if (!_declarablesLF.count(hash)) {
                if (!_msvc.count(hash)) {
                    nd4j_printf("Unknown F operation requested by hash: [%lld]\n", hash);
                    return nullptr;
                } else {
                    _locker.lock();

                    auto str = _msvc.at(hash);
                    auto op = _declarablesF.at(str);
                    auto oHash = op->getOpDescriptor()->getHash();

                    std::pair<Nd4jIndex, nd4j::ops::DeclarableOp<float>*> pair(oHash, op);
                    _declarablesLF.insert(pair);

                    _locker.unlock();
                }
            }

            return _declarablesLF.at(hash);
        }


        nd4j::ops::DeclarableOp<float16> *OpRegistrator::getOperationHalf(Nd4jIndex hash) {
            if (!_declarablesLH.count(hash)) {
                if (!_msvc.count(hash)) {
                    nd4j_printf("Unknown H operation requested by hash: [%lld]\n", hash);
                    return nullptr;
                } else {
                    _locker.lock();

                    auto str = _msvc.at(hash);
                    auto op = _declarablesH.at(str);
                    auto oHash = op->getOpDescriptor()->getHash();

                    std::pair<Nd4jIndex, nd4j::ops::DeclarableOp<float16>*> pair(oHash, op);
                    _declarablesLH.insert(pair);

                    _locker.unlock();
                }
            }

            return _declarablesLH.at(hash);
        }


        nd4j::ops::DeclarableOp<float16>* OpRegistrator::getOperationHalf(const char *name) {
            std::string str(name);
            return getOperationHalf(str);
        }

        nd4j::ops::DeclarableOp<float16> *OpRegistrator::getOperationHalf(std::string& name) {
            if (!_declarablesH.count(name)) {
                nd4j_debug("Unknown operation requested: [%s]\n", name.c_str());
                return nullptr;
            }

            return _declarablesH.at(name);
        }


        nd4j::ops::DeclarableOp<double >* OpRegistrator::getOperationDouble(const char *name) {
            std::string str(name);
            return getOperationDouble(str);
        }


        nd4j::ops::DeclarableOp<double> *OpRegistrator::getOperationDouble(Nd4jIndex hash) {
            if (!_declarablesLD.count(hash)) {
                if (!_msvc.count(hash)) {
                    nd4j_printf("Unknown D operation requested by hash: [%lld]\n", hash);
                    return nullptr;
                } else {
                    _locker.lock();

                    auto str = _msvc.at(hash);
                    auto op = _declarablesD.at(str);
                    auto oHash = op->getOpDescriptor()->getHash();

                    std::pair<Nd4jIndex, nd4j::ops::DeclarableOp<double>*> pair(oHash, op);
                    _declarablesLD.insert(pair);

                    _locker.unlock();
                }
            }

            return _declarablesLD.at(hash);
        }

        nd4j::ops::DeclarableOp<double> *OpRegistrator::getOperationDouble(std::string& name) {
            if (!_declarablesD.count(name)) {
                nd4j_debug("Unknown operation requested: [%s]\n", name.c_str());
                return nullptr;
            }

            return _declarablesD.at(name);
        }

        nd4j::ops::OpRegistrator* nd4j::ops::OpRegistrator::_INSTANCE = 0;
    }
}

