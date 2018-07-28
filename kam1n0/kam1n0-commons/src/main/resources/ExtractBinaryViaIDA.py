# *******************************************************************************
#  * Copyright 2017 McGill University All rights reserved.
#  *
#  * Licensed under the Apache License, Version 2.0 (the "License");
#  * you may not use this file except in compliance with the License.
#  * You may obtain a copy of the License at
#  *
#  *     http://www.apache.org/licenses/LICENSE-2.0
#  *
#  * Unless required by applicable law or agreed to in writing, software
#  * distributed under the License is distributed on an "AS IS" BASIS,
#  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  * See the License for the specific language governing permissions and
#  * limitations under the License.
#  *******************************************************************************/

from sets import Set
import json
import idaapi
import idc
import os

print('Kam1n0 script for idapro is now running...')
print('Waiting for idapro...')
idaapi.autoWait()
print('start persisting...')


def get_apis(func_addr):
        calls = 0
        apis = []
        flags = GetFunctionFlags(func_addr)
        # ignore library functions
        if flags & FUNC_LIB or flags & FUNC_THUNK:
            return (calls, apis)
        # list of addresses
        dism_addr = list(FuncItems(func_addr))
        for instr in dism_addr:
            tmp_api_address = ""
            if idaapi.is_call_insn(instr):
                # In theory an API address should only have one xrefs
                # The xrefs approach was used because I could not find how to
                # get the API name by address.
                for xref in XrefsFrom(instr, idaapi.XREF_FAR):
                    if xref.to == None:
                        calls += 1
                        continue
                    tmp_api_address = xref.to
                    break
                # get next instr since api address could not be found
                if tmp_api_address == "":
                    calls += 1
                    continue
                api_flags = GetFunctionFlags(tmp_api_address)
                # check for lib code (api)
                if api_flags & idaapi.FUNC_LIB == True or api_flags & idaapi.FUNC_THUNK:
                    tmp_api_name = NameEx(0, tmp_api_address)
                    if tmp_api_name:
                        apis.append(tmp_api_name)
                else:
                    calls += 1
        return (calls, apis)


rebase = int(os.getenv('K_REBASE', 0))
cleanStack = int(os.getenv('K_CLEANSTACK', 0))
if rebase == 1:
    idaapi.rebase_program(-1*idaapi.get_imagebase(), 0)

ss = os.path.splitext(idc.GetIdbPath())[0]
print(ss)
pn = os.path.splitext(ss)[0]
callees = dict()
funcmap = dict()
data = dict()
data['name'] = pn

for seg_ea in Segments():
    for function_ea in Functions(SegStart(seg_ea), SegEnd(seg_ea)):
        #fill call graph
        # For each of the incoming references
        for ref_ea in CodeRefsTo(function_ea, 0):
             # Get the name of the referring function
             caller_name = GetFunctionName(ref_ea)
             # Add the current function to the list of functions called by the referring function
             callees[caller_name] = callees.get(caller_name, Set())
             callees[caller_name].add(function_ea)
             

data['architecture'] = {}
info = idaapi.get_inf_structure()
data['architecture']['type'] = info.procName.lower();
data['architecture']['size'] = "b32"
if info.is_32bit():
    data['architecture']['size'] = "b32" 
if info.is_64bit(): 
    data['architecture']['size'] = "b64";
data['architecture']['endian'] = "be" if idaapi.cvar.inf.mf else "le";
if info.procName.lower().startswith('mips'):
    data['architecture']['type'] = 'mips'
    

data['functions'] = list()
batchSize = int(os.getenv('K_BATCHSIZE', 10000))
print 'batch size: %d' % batchSize
batchCount = 0;
for seg_ea in Segments():
    for function_ea in Functions(SegStart(seg_ea), SegEnd(seg_ea)):
        f_name = GetFunctionName(function_ea)
        function = dict();
        data['functions'].append(function)
        function['name'] = f_name
        function['id'] = function_ea
        function['call'] = list()
        function['sea'] = function_ea
        function['see'] = FindFuncEnd(function_ea)
        if callees.has_key(f_name):
            for calling in callees[f_name]:
                function['call'].append(calling)

	#optional
	function['api'] = get_apis(function_ea)[1]
        
        function['blocks'] = list()      
        funcfc = idaapi.FlowChart(idaapi.get_func(function_ea))
        #basic bloc content
        for bblock in funcfc:

            sblock = dict();
            sblock['id'] = bblock.id;
            sblock['sea'] = bblock.startEA
            if(data['architecture']['type'] == 'arm'):
               sblock['sea'] += GetReg(bblock.startEA, 'T');
            sblock['eea'] = bblock.endEA
            sblock['name'] = 'loc_' + format(bblock.startEA, 'x').upper()
            dat = {}
            sblock['dat'] = dat
            tlines = []
            
            s = GetManyBytes(bblock.startEA, bblock.endEA - bblock.startEA)
            if(s != None):
            	sblock['bytes'] = "".join("{:02x}".format(ord(c)) for c in s)
            else:
            	print sblock['name']
                
            for head in Heads(bblock.startEA, bblock.endEA):
                tline = []
                tline.append(str(hex(head)).rstrip("L").upper().replace("0X", "0x"))
                mnem = idc.GetMnem(head)
                if mnem == "":
                     continue;
                mnem = idc.GetDisasm(head).split()[0]
                tline.append(mnem)
                for i in range(5):
                     if cleanStack == 1:
                          idc.OpOff(head, i, 16)
                     opd = idc.GetOpnd(head, i)
                     if opd == "":
                          continue;
                     tline.append(opd)
                tlines.append(tline)

                refdata = list(DataRefsFrom(head))
                if(len(refdata)>0):
                    for ref in refdata:
                        dat[head] = format(Qword(ref), 'x')[::-1]
                
            sblock['src'] = tlines
                
            
            
            # flow chart
            bcalls = list()
            for succ_block in bblock.succs():
                bcalls.append(succ_block.id)
            sblock['call'] = bcalls
            function['blocks'].append(sblock)
        if len(data['functions']) % batchSize == 0:
            with open('%s.tmp%d.json' % (ss, batchCount), 'w') as outfile:
                json.dump(data, outfile)
            batchCount += 1
            del data['functions']
            data['functions'] = list()
            

with open('%s.tmp%d.json' % (ss, batchCount), 'w') as outfile:
    json.dump(data, outfile)

idc.Exit(0)
