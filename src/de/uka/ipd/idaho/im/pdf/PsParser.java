/*
 * Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Universität Karlsruhe (TH) / KIT nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITÄT KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.uka.ipd.idaho.im.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Vector;

import org.icepdf.core.pobjects.Name;

import de.uka.ipd.idaho.im.pdf.PdfUtils.PdfByteInputStream;

/**
 * @author sautter
 *
 */
public class PsParser {
	
	private static final boolean DEBUG_EXECUTE_PS = false;
	
	private static final boolean REPORT_UNKNOWN_COMMANDS = false; // TODO always set this to false for builds
	
	static void executePs(byte[] data, Hashtable state, LinkedList stack) throws IOException {
		executePs(data, state, stack, null);
	}
	
	static void executePs(byte[] data, Hashtable state, LinkedList stack, String indent) throws IOException {
		PdfByteInputStream pbis = new PdfByteInputStream(new ByteArrayInputStream(data));
		LinkedList dictStack = new LinkedList();
		for (Object next; (next = cropNext(pbis)) != null;) {
			if (next instanceof PsCommand) {
				PsCommand psc = ((PsCommand) next);
				//	TODO implement remaining commands
				
				//	skip access modifiers
				if ("readonly".equals(psc.command))
					continue;
				if ("executeonly".equals(psc.command))
					continue;
				if ("noaccess".equals(psc.command))
					continue;
				
				//	skip 'FontDirectory'
				if ("FontDirectory".equals(psc.command))
					continue;
				
				//	skip file getters (we already have the data)
				if ("currentfile".equals(psc.command))
					continue;
				if ("closefile".equals(psc.command))
					continue;
				
				//	skip marker of encrypted section
				if ("eexec".equals(psc.command))
					continue;
				
				//	start of cascade ...
				if (false) {}
				
				//	put mark on stack
				else if ("mark".equals(psc.command)) {
					stack.addLast("MARK");
				}
				
				//	pop last stack element
				else if ("pop".equals(psc.command)) {
					if (stack.size() != 0)
						stack.removeLast();
				}
				
				//	duplicate last stack element
				else if ("dup".equals(psc.command)) {
					stack.addLast(stack.getLast());
				}
				
				//	swap last two stack elements
				else if ("exch".equals(psc.command)) {
					Object last1 = stack.removeLast();
					Object last2 = stack.removeLast();
					stack.addLast(last1);
					stack.addLast(last2);
				}
				
				//	duplicate a stack element
				else if ("index".equals(psc.command)) {
					int index = ((Number) stack.removeLast()).intValue();
					stack.addLast(stack.get(stack.size() - 1 - index));
				}
				
				//	roll the n topmost stack elements by j
				else if ("roll".equals(psc.command)) {
					int j = ((Number) stack.removeLast()).intValue();
					int n = ((Number) stack.removeLast()).intValue();
					if (j < 0) {
						for (int i = 0; i < -j; i++)
							stack.addLast(stack.remove(stack.size() - n));
					}
					else if (j > 0)
						for (int i = 0; i < j; i++) {
							stack.add((stack.size() - n), stack.getLast());
							stack.removeLast();
						}
				}
				
				//	copy the topmost n stack elements
				else if ("copy".equals(psc.command)) {
					int n = ((Number) stack.removeLast()).intValue();
					for (int i = 0; i < n; i++)
						stack.addLast(stack.get(stack.size() - n));
				}
				
				
				
				//	create an array
				else if ("array".equals(psc.command)) {
					int length = ((Number) stack.removeLast()).intValue();
					stack.addLast(new Vector(length));
				}
				
				//	create a dictionary
				else if ("dict".equals(psc.command)) {
					int size = ((Number) stack.removeLast()).intValue();
					stack.addLast(new Hashtable(size));
				}
				
				//	duplicate a dictionary onto the stack
				else if ("currentdict".equals(psc.command)) {
					stack.addLast(dictStack.getLast());
				}
				
				//	put an element into an array or dictionary
				else if ("put".equals(psc.command)) {
					Object element = stack.removeLast();
					Object nameOrIndex = stack.removeLast();
					Object dictOrArray = stack.removeLast();
					try {
						if (dictOrArray instanceof Vector) {
							int index = ((Number) nameOrIndex).intValue();
							Vector array = ((Vector) dictOrArray);
							if (index < array.size())
								array.set(index, element);
							array.add(index, element);
//							System.out.println("Set " + index + " to " + element);
//							System.out.println("Stack is: " + stack);
						}
						else if (dictOrArray instanceof Hashtable) {
							Name key = ((Name) nameOrIndex);
							Hashtable dict = ((Hashtable) dictOrArray);
							dict.put(key, element);
//							System.out.println("Set " + key + " to " + element);
//							System.out.println("Stack is: " + stack);
						}
						else {
							stack.addLast(dictOrArray);
							stack.addLast(nameOrIndex);
							stack.addLast(element);
							System.out.println("Strange put stack is: " + stack);
							System.out.println("Strange put dict stack is: " + dictStack);
							System.out.println("Strange put state is: " + state);
						}
					}
					catch (RuntimeException re) {
						stack.addLast(dictOrArray);
						stack.addLast(nameOrIndex);
						stack.addLast(element);
						System.out.println("Error stack is: " + stack);
						System.out.println("Error dict stack is: " + dictStack);
						System.out.println("Error state is: " + state);
						throw re;
					}
				}
				
				//	get an element from an array or dictionary
				else if ("get".equals(psc.command)) {
					Object nameOrIndex = stack.removeLast();
					Object dictOrArray = stack.removeLast();
					try {
						if (dictOrArray instanceof Vector) {
							int index = ((Number) nameOrIndex).intValue();
							Vector array = ((Vector) dictOrArray);
							stack.addLast(array.get(index));
						}
						else if (dictOrArray instanceof Hashtable) {
							Name key = ((Name) nameOrIndex);
							Hashtable dict = ((Hashtable) dictOrArray);
							Object value = dict.get(key);
							stack.addLast(value);
						}
						else {
							stack.addLast(dictOrArray);
							stack.addLast(nameOrIndex);
							System.out.println("Strange get stack is: " + stack);
							System.out.println("Strange get dict stack is: " + dictStack);
							System.out.println("Strange get state is: " + state);
						}
					}
					catch (RuntimeException re) {
						stack.addLast(dictOrArray);
						stack.addLast(nameOrIndex);
						System.out.println("Error stack is: " + stack);
						System.out.println("Error dict stack is: " + dictStack);
						System.out.println("Error state is: " + state);
						throw re;
					}
				}
				
				//	define a variable in state
				else if ("def".equals(psc.command)) {
					Object value = stack.removeLast();
					Name key = ((Name) stack.removeLast());
					Hashtable dict = (dictStack.isEmpty() ? state : ((Hashtable) dictStack.getLast()));
					dict.put(key, value);
//					System.out.println("Set " + key + " to " + value);
//					System.out.println("Stack is: " + stack);
				}
				
				//	known command
				else if ("known".equals(psc.command)) {
					Name key = ((Name) stack.removeLast());
					Hashtable dict = (dictStack.isEmpty() ? state : ((Hashtable) dictStack.getLast()));
					stack.addLast(new Boolean(dict.containsKey(key)));
				}
				
				//	push dictionary on dedicated stack
				else if ("begin".equals(psc.command)) {
					Hashtable dict = ((Hashtable) stack.removeLast());
					dictStack.addLast(dict);
				}
				
				//	pop dictionary off dedicated stack
				else if ("end".equals(psc.command)) {
					Hashtable dict = ((Hashtable) dictStack.removeLast());
//					System.out.println("Got dictionary: " + dict);
				}
				
				
				
				//	if command
				else if ("if".equals(psc.command)) {
					System.out.println("If stack is: " + stack);
					Object tObj = stack.removeLast();
					Object bObj = stack.removeLast();
					while (bObj instanceof PsProcedure) {
						PsProcedure bProc = ((PsProcedure) bObj);
						System.out.println("Executing " + bProc.toString());
						bProc.execute(state, stack);
						bObj = stack.removeLast();
					}
					if (((Boolean) bObj).booleanValue()) {
						if (tObj instanceof PsProcedure) {
							PsProcedure tProc = ((PsProcedure) tObj);
							System.out.println("Executing " + tProc.toString());
							tProc.execute(state, stack);
						}
						else stack.addLast(tObj);
					}
				}
				
				//	if/else command
				else if ("ifelse".equals(psc.command)) {
					System.out.println("IfElse stack is: " + stack);
					Object fObj = stack.removeLast();
					Object tObj = stack.removeLast();
					Object bObj = stack.removeLast();
					while (bObj instanceof PsProcedure) {
						PsProcedure bProc = ((PsProcedure) bObj);
						System.out.println("Executing " + bProc.toString());
						bProc.execute(state, stack);
						bObj = stack.removeLast();
					}
					if (((Boolean) bObj).booleanValue()) {
						if (tObj instanceof PsProcedure) {
							PsProcedure tProc = ((PsProcedure) tObj);
							System.out.println("Executing " + tProc.toString());
							tProc.execute(state, stack);
						}
						else stack.addLast(tObj);
					}
					else {
						if (fObj instanceof PsProcedure) {
							PsProcedure fProc = ((PsProcedure) fObj);
							System.out.println("Executing " + fProc.toString());
							fProc.execute(state, stack);
						}
						else stack.addLast(fObj);
					}
				}
				
				
				
				//	the usual RD, ND, and NP (always named that in Type1 fonts)
				else if ("RD".equals(psc.command)) {
					int length = ((Number) stack.removeLast()).intValue();
					while (pbis.peek() == 32)
						pbis.read();
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					for (int l = 0; l < length; l++)
						baos.write(pbis.read());
					byte[] stopPeek = new byte[3];
					while (true) {
						pbis.peek(stopPeek);
						if (PdfUtils.equals(stopPeek, " ND"))
							break;
						else if (PdfUtils.equals(stopPeek, " NP"))
							break;
						else baos.write(pbis.read());
					}
					stack.addLast(new PsString(baos.toByteArray()));
				}
				else if ("ND".equals(psc.command)) {
					Object value = stack.removeLast();
					Name key = ((Name) stack.removeLast());
					Hashtable dict = (dictStack.isEmpty() ? state : ((Hashtable) dictStack.getLast()));
					dict.put(key, value);
//					System.out.println("Set " + key + " to " + value);
				}
				else if ("NP".equals(psc.command)) {
					Object element = stack.removeLast();
					int index = ((Number) stack.removeLast()).intValue();
					Vector array = ((Vector) stack.removeLast());
					array.add(index, element);
				}
				
				//	define font
				else if ("definefont".equals(psc.command)) {
					Object font = stack.removeLast();
					Object key = stack.removeLast();
					state.put(key, font);
				}
				
				
				
				//	get absolute value of last stack element
				else if ("abs".equals(psc.command))
					stack.addLast(new Float(Math.abs(((Number) stack.removeLast()).floatValue())));
				
				//	negate last stack element
				else if ("neg".equals(psc.command))
					stack.addLast(new Float(-((Number) stack.removeLast()).floatValue()));
				
				//	round last stack element
				else if ("round".equals(psc.command))
					stack.addLast(new Float(Math.round(((Number) stack.removeLast()).floatValue())));
				
				//	round down last stack element
				else if ("floor".equals(psc.command))
					stack.addLast(new Float(Math.floor(((Number) stack.removeLast()).floatValue())));
				
				//	round up last stack element
				else if ("ceiling".equals(psc.command))
					stack.addLast(new Float(Math.ceil(((Number) stack.removeLast()).floatValue())));
				
				//	compute square root of last stack element
				else if ("sqrt".equals(psc.command))
					stack.addLast(new Float(Math.sqrt(((Number) stack.removeLast()).floatValue())));
				
				//	compute natural logarithm of last stack element
				else if ("ln".equals(psc.command))
					stack.addLast(new Float(Math.log(((Number) stack.removeLast()).floatValue())));
				
				//	compute 10 logarithm of last stack element
				else if ("log".equals(psc.command))
					stack.addLast(new Float(Math.log10(((Number) stack.removeLast()).floatValue())));
				
				//	truncate last stack element
				else if ("truncate".equals(psc.command)) {
					float num = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float((num < 0) ? Math.ceil(num) : Math.floor(num)));
				}
				
				//	compute sine of last stack element (in degrees)
				else if ("sin".equals(psc.command))
					stack.addLast(new Float(Math.sin((((Number) stack.removeLast()).floatValue() * Math.PI) / 180)));
				
				//	compute cosine of last stack element (in degrees)
				else if ("cos".equals(psc.command))
					stack.addLast(new Float(Math.cos((((Number) stack.removeLast()).floatValue() * Math.PI) / 180)));
				
				//	convert real to integer
				else if ("cvi".equals(psc.command))
					stack.addLast(new Integer(((Number) stack.removeLast()).intValue()));
				
				//	convert integer to real
				else if ("cvr".equals(psc.command))
					stack.addLast(new Float(((Number) stack.removeLast()).floatValue()));
				
				
				//	add last two stack elements
				else if ("add".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float(num1 + num2));
				}
				
				//	subtract last two stack elements from one another
				else if ("sub".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float(num1 - num2));
				}
				
				//	multiply last two stack elements
				else if ("mul".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float(num1 * num2));
				}
				
				//	divide last two stack elements by one another
				else if ("div".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float(num1 / num2));
				}
				
				//	integer divide last two stack elements by one another
				else if ("idiv".equals(psc.command)) {
					int num2 = ((Number) stack.removeLast()).intValue();
					int num1 = ((Number) stack.removeLast()).intValue();
					stack.addLast(new Integer(num1 / num2));
				}
				
				//	take last two stack elements modulo one another
				else if ("mod".equals(psc.command)) {
					int num2 = ((Number) stack.removeLast()).intValue();
					int num1 = ((Number) stack.removeLast()).intValue();
					stack.addLast(new Integer(num1 % num2));
				}
				
				//	power last two stack elements with one another
				else if ("exp".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float(Math.pow(num1, num2)));
				}
				
				//	compute arcus tangens of last two stack elements (in degrees)
				else if ("atan".equals(psc.command)) {
					float num2 = ((Number) stack.removeLast()).floatValue();
					float num1 = ((Number) stack.removeLast()).floatValue();
					stack.addLast(new Float((Math.atan2(num1, num2) * 180) / Math.PI));
				}
				
				
				
				//	compute logical or bit-wise complement of last stack element
				else if ("not".equals(psc.command)) {
					Object obj = stack.removeLast();
					if (obj instanceof Number)
						stack.addLast(new Integer(~((Number) obj).intValue()));
					else if (obj instanceof Boolean)
						stack.addLast(new Boolean(((Boolean) obj).booleanValue() != true));
					else throw new IllegalArgumentException("Cannot negate " + obj);
				}
				
				//	logically or bit-wise 'and' combine last two stack elements
				else if ("and".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Integer(((Number) obj1).intValue() & ((Number) obj2).intValue()));
					else if ((obj1 instanceof Boolean) && (obj2 instanceof Boolean))
						stack.addLast(new Boolean(((Boolean) obj1).booleanValue() && ((Boolean) obj2).booleanValue()));
					else throw new IllegalArgumentException("Cannot 'and' combine " + obj1 + " and " + obj2);
				}
				
				//	logically or bit-wise 'or' combine last two stack elements
				else if ("or".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Integer(((Number) obj1).intValue() | ((Number) obj2).intValue()));
					else if ((obj1 instanceof Boolean) && (obj2 instanceof Boolean))
						stack.addLast(new Boolean(((Boolean) obj1).booleanValue() || ((Boolean) obj2).booleanValue()));
					else throw new IllegalArgumentException("Cannot 'or' combine " + obj1 + " and " + obj2);
				}
				
				//	logically or bit-wise 'xor' combine last two stack elements
				else if ("xor".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Integer(((Number) obj1).intValue() ^ ((Number) obj2).intValue()));
					else if ((obj1 instanceof Boolean) && (obj2 instanceof Boolean))
						stack.addLast(new Boolean(((Boolean) obj1).booleanValue() != ((Boolean) obj2).booleanValue()));
					else throw new IllegalArgumentException("Cannot 'xor' combine " + obj1 + " and " + obj2);
				}
				
				//	bit-shift last two stack elements by one another
				else if ("bitshift".equals(psc.command)) {
					int num2 = ((Number) stack.removeLast()).intValue();
					int num1 = ((Number) stack.removeLast()).intValue();
					if (num2 < 0)
						stack.addLast(new Integer(num1 >>> -num2));
					else stack.addLast(new Integer(num1 << num2));
				}
				
				//	test if last two stack elements are equal
				else if ("eq".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() == ((Number) obj2).floatValue()));
					else if (((obj1 instanceof PsString) || (obj1 instanceof Name)) && ((obj2 instanceof PsString) || (obj2 instanceof Name)))
						stack.addLast(new Boolean(obj1.toString().compareTo(obj2.toString()) == 0));
					else stack.addLast(new Boolean(obj1.equals(obj2)));
				}
				
				//	test if last two stack elements are not equal
				else if ("ne".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() != ((Number) obj2).floatValue()));
					else if (((obj1 instanceof PsString) || (obj1 instanceof Name)) && ((obj2 instanceof PsString) || (obj2 instanceof Name)))
						stack.addLast(new Boolean(obj1.toString().compareTo(obj2.toString()) != 0));
					else stack.addLast(new Boolean(!obj1.equals(obj2)));
				}
				
				//	compare last two stack elements
				else if ("gt".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() > ((Number) obj2).floatValue()));
					else if ((obj1 instanceof PsString) && (obj2 instanceof PsString))
						stack.addLast(new Boolean(((PsString) obj1).toString().compareTo(((PsString) obj2).toString()) > 0));
					else throw new IllegalArgumentException("Cannot compare " + obj1 + " and " + obj2);
				}
				else if ("ge".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() >= ((Number) obj2).floatValue()));
					else if ((obj1 instanceof PsString) && (obj2 instanceof PsString))
						stack.addLast(new Boolean(((PsString) obj1).toString().compareTo(((PsString) obj2).toString()) >= 0));
					else throw new IllegalArgumentException("Cannot compare " + obj1 + " and " + obj2);
				}
				else if ("eq".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() == ((Number) obj2).floatValue()));
					else if ((obj1 instanceof PsString) && (obj2 instanceof PsString))
						stack.addLast(new Boolean(((PsString) obj1).toString().compareTo(((PsString) obj2).toString()) == 0));
					else throw new IllegalArgumentException("Cannot compare " + obj1 + " and " + obj2);
				}
				else if ("le".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() <= ((Number) obj2).floatValue()));
					else if ((obj1 instanceof PsString) && (obj2 instanceof PsString))
						stack.addLast(new Boolean(((PsString) obj1).toString().compareTo(((PsString) obj2).toString()) <= 0));
					else throw new IllegalArgumentException("Cannot compare " + obj1 + " and " + obj2);
				}
				else if ("lt".equals(psc.command)) {
					Object obj2 = stack.removeLast();
					Object obj1 = stack.removeLast();
					if ((obj1 instanceof Number) && (obj2 instanceof Number))
						stack.addLast(new Boolean(((Number) obj1).floatValue() < ((Number) obj2).floatValue()));
					else if ((obj1 instanceof PsString) && (obj2 instanceof PsString))
						stack.addLast(new Boolean(((PsString) obj1).toString().compareTo(((PsString) obj2).toString()) < 0));
					else throw new IllegalArgumentException("Cannot compare " + obj1 + " and " + obj2);
				}
				
				
				
				//	user defined command
				else if ((dictStack.isEmpty() ? state : ((Hashtable) dictStack.getLast())).containsKey(psc.command)) {
					Object obj = (dictStack.isEmpty() ? state : ((Hashtable) dictStack.getLast())).get(psc.command);
					System.out.println("Got " + psc.command + ": " + obj.toString());
					PsProcedure proc = ((PsProcedure) obj);
					System.out.println("Executing " + proc.toString());
					proc.execute(state, stack);
				}
				
				//	other command, yet to be implements
				else {
					if (REPORT_UNKNOWN_COMMANDS)
						throw new RuntimeException("Unknown PostScript command: " + psc.command);
					System.out.println("Other command: " + psc.command);
				}
				
				if (indent != null)
					System.out.println(indent + psc.command + " ==> " + stack);
			}
			else {
				stack.addLast(next);
				if (indent != null)
					System.out.println(indent + next + " ==> " + stack);
			}
		}
//		System.out.println("Final stack is: " + stack);
//		System.out.println("Final dict stack is: " + dictStack);
//		System.out.println("Final state is: " + state);
	}
	
	private static final boolean DEBUG_PARSE_PS = false;
	
	private static Object cropNext(PdfByteInputStream bytes) throws IOException {
		if (!bytes.skipSpaceCheckEnd())
			return null;
		
		//	skip any comment
		while (bytes.peek() == '%') {
			skipComment(bytes);
			if (!bytes.skipSpaceCheckEnd())
				return null;
		}
		
		//	hex string, dictionary, or stream parameters
		if (bytes.peek() == '<') {
			bytes.read();
			if (bytes.peek() == '<') {
				bytes.read();
				return cropHashtable(bytes);
			}
			else return cropHexString(bytes);
		}
		
		//	procedure
		else if (bytes.peek() == '{') {
			bytes.read();
			return cropProcedure(bytes);
		}
		
		//	string
		else if (bytes.peek() == '(') {
			bytes.read();
			return cropString(bytes);
		}
		
		//	array
		else if (bytes.peek() == '[') {
			bytes.read();
			return cropArray(bytes);
		}
		
		//	name
		else if (bytes.peek() == '/') {
			return cropName(bytes);
		}
		
		//	boolean, number, null, or function call
		else {
			byte[] lookahead = new byte[16]; // 16 bytes is reasonable for detecting references, as object number and generation number combined will rarely exceed 13 digits
			int l = bytes.peek(lookahead);
			if (l == -1)
				return null;
			if (DEBUG_PARSE_PS) System.out.println("Got lookahead: " + new String(lookahead));
			
			if (DEBUG_PARSE_PS) System.out.println(" --> other object");
			StringBuffer valueBuffer = new StringBuffer();
			while ((bytes.peek() > 32) && ("%()<>[]{}/#".indexOf((char) bytes.peek()) == -1))
				valueBuffer.append((char) bytes.read());
			String value = valueBuffer.toString();
			if (DEBUG_PARSE_PS) System.out.println(" --> value: " + value);
			if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> boolean");
				return new Boolean(value);
			}
			else if ("null".equalsIgnoreCase(value)) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> null");
				return NULL;
			}
			else if ("FontDirectory".equals(value)) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> command");
				return new PsCommand(value);
			}
			else if (value.matches("[A-Za-z]+")) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> command");
				return new PsCommand(value);
			}
			else if (value.matches("\\-?[0-9]++")) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> integer");
				return new Integer(value);
			}
			else if (value.matches("\\-?[0-9]*+\\.[0-9]++")) {
				if (DEBUG_PARSE_PS) System.out.println(" ==> double");
				return new Double(value);
			}
			else {
				if (DEBUG_PARSE_PS) System.out.println(" ==> double");
				return new Double(value);
			}
		}
	}
	private static final Object NULL = new Object();
	
	private static void skipComment(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Skipping comment");
		StringBuffer comment = new StringBuffer();
		while (bytes.peek() != -1) {
			if ((bytes.peek() == '\r') || bytes.peek() == '\n')
				break;
			comment.append((char) bytes.read());
		}
		while ((bytes.peek() == '\r') || (bytes.peek() == '\n'))
			bytes.read();
		if (DEBUG_PARSE_PS) System.out.println(" --> " + comment.toString());
	}
	
	private static Name cropName(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping name");
		if (bytes.peek() == '/')
			bytes.read();
		StringBuffer name = new StringBuffer();
		while (true) {
			if ((bytes.peek() < 33) || ("%()<>[]{}/".indexOf((char) bytes.peek()) != -1))
				break;
			if (bytes.peek() == '#') {
				bytes.read();
				int hb = bytes.read();
				int lb = bytes.read();
				if (checkHexByte(hb) && checkHexByte(lb))
					name.append((char) translateHexBytes(hb, lb));
				else throw new IOException("Invalid escaped character #" + ((char) hb) + "" + ((char) lb));
			}
			else name.append((char) bytes.read());
		}
		if (DEBUG_PARSE_PS) System.out.println(" --> " + name.toString());
		return new Name(name.toString());
	}
	
	private static boolean checkHexByte(int b) {
		if ((b <= '9') && ('0' <= b))
			return true;
		else if ((b <= 'f') && ('a' <= b))
			return true;
		else if ((b <= 'F') && ('A' <= b))
			return true;
		else return false;
	}
	private static int translateHexBytes(int hb, int lb) {
		int v = 0;
		v += translateHexByte(hb);
		v <<= 4;
		v += translateHexByte(lb);
		return v;
	}
	private static int translateHexByte(int b) {
		if (('0' <= b) && (b <= '9')) return (((int) b) - '0');
		else if (('a' <= b) && (b <= 'f')) return (((int) b) - 'a' + 10);
		else if (('A' <= b) && (b <= 'F')) return (((int) b) - 'A' + 10);
		else return 0;
	}
	
	//	TODOne decode bytes only when encoding clear
	private static PsString cropString(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping string");
		ByteArrayOutputStream oBaos = (DEBUG_PARSE_PS ? new ByteArrayOutputStream() : null);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int open = 1;
		boolean escaped = false;
		while (bytes.peek() != -1) {
			if (escaped) {
				int peek = bytes.peek();
				if (('0' <= peek) && (peek <= '9')) {
					int oct = 0;
					if (oBaos != null) oBaos.write(bytes.peek());
					int b0 = bytes.read();
					peek = bytes.peek();
					if (('0' <= peek) && (peek <= '9')) {
						if (oBaos != null) oBaos.write(bytes.peek());
						int b1 = bytes.read();
						peek = bytes.peek();
						if (('0' <= peek) && (peek <= '9')) {
							if (oBaos != null) oBaos.write(bytes.peek());
							int b2 = bytes.read();
							oct = (((b0 - '0') * 64) + ((b1 - '0') * 8) + (b2 - '0'));
						}
						else oct = (((b0 - '0') * 8) + (b1 - '0'));
					}
					else oct = (b0 - '0');
					if (oct > 127)
						oct -= 256;
					baos.write(oct);
				}
				else if (peek == 'n') {
					baos.write('\n');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 'r') {
					baos.write('\r');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 't') {
					baos.write('\t');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 'f') {
					baos.write('\f');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == 'b') {
					baos.write('\b');
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
				}
				else if (peek == '\r') {
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
					peek = bytes.peek();
					if (peek == '\n') {
						if (oBaos != null) oBaos.write(bytes.peek());
						bytes.read();
					}
				}
				else if (peek == '\n') {
					if (oBaos != null) oBaos.write(bytes.peek());
					bytes.read();
					peek = bytes.peek();
					if (peek == '\r') {
						if (oBaos != null) oBaos.write(bytes.peek());
						bytes.read();
					}
				}
				else {
					if (oBaos != null) oBaos.write(bytes.peek());
					baos.write(bytes.read());
				}
				escaped = false;
			}
			else if (bytes.peek() == '\\') {
				escaped = true;
				if (oBaos != null) oBaos.write(bytes.peek());
				bytes.read();
			}
			else if (bytes.peek() == '(') {
				if (oBaos != null) oBaos.write(bytes.peek());
				baos.write(bytes.read());
				open++;
			}
			else if (bytes.peek() == ')') {
				open--;
				if (open == 0) {
					bytes.read();
					break;
				}
				else {
					if (oBaos != null) oBaos.write(bytes.peek());
					baos.write(bytes.read());
				}
			}
			else {
				if (oBaos != null) oBaos.write(bytes.peek());
				baos.write(bytes.read());
			}
		}
		if (DEBUG_PARSE_PS) {
			System.out.println(" -->  " + Arrays.toString(oBaos.toByteArray()));
			System.out.println(" ==> " + Arrays.toString(baos.toByteArray()));
			System.out.println("  ANSI " + new String(baos.toByteArray()));
//			System.out.println("  UTF-16LE " + new String(baos.toByteArray(), "UTF-16LE"));
//			System.out.println("  UTF-16BE " + new String(baos.toByteArray(), "UTF-16BE"));
		}
		return new PsString(baos.toByteArray());
	}
	
	private static PsString cropHexString(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping hex string");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		boolean withSpace = false;
		while (bytes.peek() != -1) {
			withSpace = ((bytes.peek() < 33) || withSpace);
			bytes.skipSpace();
			if (bytes.peek() == '>') {
				bytes.read();
				break;
			}
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			baos.write(bytes.read());
		}
		return new PsString(baos.toByteArray(), true);
	}
	
	private static PsProcedure cropProcedure(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping procedure");
//		ByteArrayOutputStream oBaos = (DEBUG_PARSE_PS ? new ByteArrayOutputStream() : null);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int open = 1;
		boolean escaped = false;
		while (bytes.peek() != -1) {
			if (escaped) {
//				int peek = bytes.peek();
//				if (('0' <= peek) && (peek <= '9')) {
//					int oct = 0;
//					if (oBaos != null) oBaos.write(bytes.peek());
//					int b0 = bytes.read();
//					peek = bytes.peek();
//					if (('0' <= peek) && (peek <= '9')) {
//						if (oBaos != null) oBaos.write(bytes.peek());
//						int b1 = bytes.read();
//						peek = bytes.peek();
//						if (('0' <= peek) && (peek <= '9')) {
//							if (oBaos != null) oBaos.write(bytes.peek());
//							int b2 = bytes.read();
//							oct = (((b0 - '0') * 64) + ((b1 - '0') * 8) + (b2 - '0'));
//						}
//						else oct = (((b0 - '0') * 8) + (b1 - '0'));
//					}
//					else oct = (b0 - '0');
//					if (oct > 127)
//						oct -= 256;
//					baos.write(oct);
//				}
//				else if (peek == 'n') {
//					baos.write('\n');
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//				}
//				else if (peek == 'r') {
//					baos.write('\r');
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//				}
//				else if (peek == 't') {
//					baos.write('\t');
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//				}
//				else if (peek == 'f') {
//					baos.write('\f');
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//				}
//				else if (peek == 'b') {
//					baos.write('\b');
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//				}
//				else if (peek == '\r') {
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//					peek = bytes.peek();
//					if (peek == '\n') {
//						if (oBaos != null) oBaos.write(bytes.peek());
//						bytes.read();
//					}
//				}
//				else if (peek == '\n') {
//					if (oBaos != null) oBaos.write(bytes.peek());
//					bytes.read();
//					peek = bytes.peek();
//					if (peek == '\r') {
//						if (oBaos != null) oBaos.write(bytes.peek());
//						bytes.read();
//					}
//				}
//				else {
//					if (oBaos != null) oBaos.write(bytes.peek());
//					baos.write(bytes.read());
//				}
//				escaped = false;
			}
//			else if (bytes.peek() == '\\') {
//				escaped = true;
//				if (oBaos != null) oBaos.write(bytes.peek());
//				bytes.read();
//			}
			else if (bytes.peek() == '{') {
//				if (oBaos != null) oBaos.write(bytes.peek());
				baos.write(bytes.read());
				open++;
			}
			else if (bytes.peek() == '}') {
				open--;
				if (open == 0) {
					bytes.read();
					break;
				}
				else {
//					if (oBaos != null) oBaos.write(bytes.peek());
					baos.write(bytes.read());
				}
			}
			else {
//				if (oBaos != null) oBaos.write(bytes.peek());
				baos.write(bytes.read());
			}
		}
		if (DEBUG_PARSE_PS) {
//			System.out.println(" -->  " + Arrays.toString(oBaos.toByteArray()));
			System.out.println(" ==> " + Arrays.toString(baos.toByteArray()));
			System.out.println("  ANSI " + new String(baos.toByteArray()));
//			System.out.println("  UTF-16LE " + new String(baos.toByteArray(), "UTF-16LE"));
//			System.out.println("  UTF-16BE " + new String(baos.toByteArray(), "UTF-16BE"));
		}
		return new PsProcedure(baos.toByteArray());
	}
	
	private static Vector cropArray(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping array");
		Vector array = new Vector(2);
		while (bytes.peek() != -1) {
			if (!bytes.skipSpaceCheckEnd())
				break;
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken array");
			}
			if (bytes.peek() == ']') {
				bytes.read();
				break;
			}
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken array");
			}
			array.add(cropNext(bytes));
		}
		if (DEBUG_PARSE_PS) System.out.println(" --> " + array.toString());
		return array;
	}
	
	private static Hashtable cropHashtable(PdfByteInputStream bytes) throws IOException {
		if (DEBUG_PARSE_PS) System.out.println("Cropping dictionary");
		Hashtable ht = new Hashtable(2);
		while (true) {
			if (!bytes.skipSpaceCheckEnd())
				throw new IOException("Broken dictionary");
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			if (bytes.peek() == '>') {
				bytes.read();
				if (bytes.peek() == '>') {
					bytes.read();
					break;
				}
				else throw new IOException("Broken dictionary");
			}
			if (!bytes.skipSpaceCheckEnd())
				throw new IOException("Broken dictionary");
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			Name name = cropName(bytes);
			if (!bytes.skipSpaceCheckEnd())
				throw new IOException("Broken dictionary");
			while (bytes.peek() == '%') {
				skipComment(bytes);
				if (!bytes.skipSpaceCheckEnd())
					throw new IOException("Broken dictionary");
			}
			Object value = cropNext(bytes);
			ht.put(name, value);
		}
		if (DEBUG_PARSE_PS) System.out.println(" --> " + ht.toString());
		return ht;
	}
	
	/**
	 * Wrapper object for a raw, undecoded string object.
	 * 
	 * @author sautter
	 */
	public static class PsString implements CharSequence {
		
		/** the raw binary data */
		public final byte[] bytes;
		
		/** does the binary data come from a HEX with 2 bytes per output byte object? */
		public final boolean isHex;
		
		PsString(byte[] bytes) {
			this(bytes, false);
		}
		PsString(byte[] bytes, boolean isHex) {
			this.bytes = bytes;
			this.isHex = isHex;
		}
		public int hashCode() {
			return this.toString().hashCode();
		}
		public boolean equals(Object obj) {
			if (obj instanceof CharSequence) {
				CharSequence cs = ((CharSequence) obj);
				if (cs.length() != this.length())
					return false;
				for (int c = 0; c < cs.length(); c++) {
					if (this.charAt(c) != cs.charAt(c))
						return false;
				}
				return true;
			}
			return false;
		}
		public String toString() {
			StringBuffer string = new StringBuffer();
			if (this.isHex) {
				for (int b = 0; b < this.bytes.length; b += 2)
					string.append((char) this.getHex(b));
			}
			else for (int c = 0; c < this.bytes.length; c++)
				string.append((char) convertUnsigned(this.bytes[c]));
			return string.toString();
		}
		public int length() {
			return (this.isHex ? ((this.bytes.length + 1) / 2) : this.bytes.length);
		}
		public char charAt(int index) {
			if (this.isHex)
				return ((char) this.getHex(index * 2));
			else return ((char) convertUnsigned(this.bytes[index]));
		}
		public CharSequence subSequence(int start, int end) {
			if (this.isHex) {
				start *= 2;
				end *= 2;
				if (end > this.bytes.length)
					end--;
			}
			byte[] bytes = new byte[end - start];
			System.arraycopy(this.bytes, start, bytes, 0, (end - start));
			return new PsString(bytes, this.isHex);
		}
		private int getHex(int index) {
			int ch = 0;
			if (('0' <= this.bytes[index]) && (this.bytes[index] <= '9'))
				ch += (this.bytes[index] - '0');
			else if (('A' <= this.bytes[index]) && (this.bytes[index] <= 'F'))
				ch += (this.bytes[index] - 'A' + 10);
			else if (('a' <= this.bytes[index]) && (this.bytes[index] <= 'f'))
				ch += (this.bytes[index] - 'a' + 10);
			index++;
			ch <<= 4;
			if (index < this.bytes.length) {
				if (('0' <= this.bytes[index]) && (this.bytes[index] <= '9'))
					ch += (this.bytes[index] - '0');
				else if (('A' <= this.bytes[index]) && (this.bytes[index] <= 'F'))
					ch += (this.bytes[index] - 'A' + 10);
				else if (('a' <= this.bytes[index]) && (this.bytes[index] <= 'f'))
					ch += (this.bytes[index] - 'a' + 10);
			}
			return ch;
		}
		private static final int convertUnsigned(byte b) {
			return ((b < 0) ? (((int) b) + 256) : b);
		}
	}
	
	/**
	 * Wrapper object for a PostScript command.
	 * 
	 * @author sautter
	 */
	public static class PsCommand {
		public final String command;
		PsCommand(String command) {
			this.command = command;
		}
	}
	
	/**
	 * Wrapper object for a raw, undecoded procedure object or named object.
	 * 
	 * @author sautter
	 */
	public static class PsProcedure {
		public final byte[] bytes;
		PsProcedure(byte[] bytes) {
			this.bytes = bytes;
		}
		public String toString() {
			return ("{" + new String(this.bytes) + "}");
		}
		public void execute(Hashtable state, LinkedList stack) throws IOException {
			executePs(this.bytes, state, stack);
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		Hashtable state = new Hashtable();
		LinkedList stack = new LinkedList();
		executePs(testData.getBytes(), state, stack);
		executePs(testData2.getBytes(), state, stack);
	}
	
	private static String testData = "%!PS-AdobeFont-1.0: JSKAWA+RealpageTIM5 1.000\r\n" + 
		"FontDirectory/JSKAWA+RealpageTIM5 known{/JSKAWA+RealpageTIM5 findfont dup/UniqueID known{dup\r\n" + 
		"/UniqueID get 630181 eq exch/FontType get 1 eq and}{pop false}ifelse\r\n" + 
		"{save true}{false}ifelse}{false}ifelse\r\n" + 
		"17 dict begin\r\n" + 
		"/FontInfo 13 dict dup begin\r\n" + 
		"/version(1.000)readonly def\r\n" + 
		"/Notice(Catchword free font)readonly def\r\n" + 
		"/FullName(JSKAWA+RealpageTIM5)readonly def\r\n" + 
		"/FamilyName(JSKAWA+RealpageTIM5)readonly def\r\n" + 
		"/Weight(Normal)readonly def\r\n" + 
		"/isFixedPitch false def\r\n" + 
		"/ItalicAngle 0 def\r\n" + 
		"/UnderlinePosition -100 def\r\n" + 
		"/UnderlineThickness 50 def\r\n" + 
		"end readonly def\r\n" + 
		"/FontName /JSKAWA+RealpageTIM5 def\r\n" + 
		"/Encoding 256 array\r\n" + 
		"dup 0/.notdef put\r\n" + 
		"dup 1/.notdef put\r\n" + 
		"dup 2/.notdef put\r\n" + 
		"dup 3/.notdef put\r\n" + 
		"dup 4/.notdef put\r\n" + 
		"dup 5/.notdef put\r\n" + 
		"dup 6/.notdef put\r\n" + 
		"dup 7/.notdef put\r\n" + 
		"dup 8/.notdef put\r\n" + 
		"dup 9/.notdef put\r\n" + 
		"dup 10/.notdef put\r\n" + 
		"dup 11/.notdef put\r\n" + 
		"dup 12/.notdef put\r\n" + 
		"dup 13/.notdef put\r\n" + 
		"dup 14/.notdef put\r\n" + 
		"dup 15/.notdef put\r\n" + 
		"dup 16/.notdef put\r\n" + 
		"dup 17/.notdef put\r\n" + 
		"dup 18/.notdef put\r\n" + 
		"dup 19/.notdef put\r\n" + 
		"dup 20/.notdef put\r\n" + 
		"dup 21/.notdef put\r\n" + 
		"dup 22/.notdef put\r\n" + 
		"dup 23/.notdef put\r\n" + 
		"dup 24/.notdef put\r\n" + 
		"dup 25/.notdef put\r\n" + 
		"dup 26/.notdef put\r\n" + 
		"dup 27/.notdef put\r\n" + 
		"dup 28/.notdef put\r\n" + 
		"dup 29/.notdef put\r\n" + 
		"dup 30/.notdef put\r\n" + 
		"dup 31/.notdef put\r\n" + 
		"dup 32/.notdef put\r\n" + 
		"dup 33/.notdef put\r\n" + 
		"dup 34/.notdef put\r\n" + 
		"dup 35/.notdef put\r\n" + 
		"dup 36/.notdef put\r\n" + 
		"dup 37/.notdef put\r\n" + 
		"dup 38/ampersand  put\r\n" + 
		"dup 39/quoteright  put\r\n" + 
		"dup 40/parenleft  put\r\n" + 
		"dup 41/parenright  put\r\n" + 
		"dup 42/.notdef put\r\n" + 
		"dup 43/.notdef put\r\n" + 
		"dup 44/comma  put\r\n" + 
		"dup 45/hyphen  put\r\n" + 
		"dup 46/period  put\r\n" + 
		"dup 47/.notdef put\r\n" + 
		"dup 48/zero  put\r\n" + 
		"dup 49/one  put\r\n" + 
		"dup 50/two  put\r\n" + 
		"dup 51/three  put\r\n" + 
		"dup 52/four  put\r\n" + 
		"dup 53/five  put\r\n" + 
		"dup 54/six  put\r\n" + 
		"dup 55/seven  put\r\n" + 
		"dup 56/eight  put\r\n" + 
		"dup 57/nine  put\r\n" + 
		"dup 58/colon  put\r\n" + 
		"dup 59/semicolon  put\r\n" + 
		"dup 60/.notdef put\r\n" + 
		"dup 61/.notdef put\r\n" + 
		"dup 62/.notdef put\r\n" + 
		"dup 63/.notdef put\r\n" + 
		"dup 64/.notdef put\r\n" + 
		"dup 65/A  put\r\n" + 
		"dup 66/B  put\r\n" + 
		"dup 67/C  put\r\n" + 
		"dup 68/D  put\r\n" + 
		"dup 69/E  put\r\n" + 
		"dup 70/F  put\r\n" + 
		"dup 71/G  put\r\n" + 
		"dup 72/H  put\r\n" + 
		"dup 73/I  put\r\n" + 
		"dup 74/J  put\r\n" + 
		"dup 75/K  put\r\n" + 
		"dup 76/L  put\r\n" + 
		"dup 77/M  put\r\n" + 
		"dup 78/N  put\r\n" + 
		"dup 79/O  put\r\n" + 
		"dup 80/P  put\r\n" + 
		"dup 81/.notdef put\r\n" + 
		"dup 82/R  put\r\n" + 
		"dup 83/S  put\r\n" + 
		"dup 84/T  put\r\n" + 
		"dup 85/U  put\r\n" + 
		"dup 86/V  put\r\n" + 
		"dup 87/W  put\r\n" + 
		"dup 88/X  put\r\n" + 
		"dup 89/.notdef put\r\n" + 
		"dup 90/Z  put\r\n" + 
		"dup 91/.notdef put\r\n" + 
		"dup 92/.notdef put\r\n" + 
		"dup 93/.notdef put\r\n" + 
		"dup 94/.notdef put\r\n" + 
		"dup 95/.notdef put\r\n" + 
		"dup 96/grave  put\r\n" + 
		"dup 97/a  put\r\n" + 
		"dup 98/b  put\r\n" + 
		"dup 99/c  put\r\n" + 
		"dup 100/d  put\r\n" + 
		"dup 101/e  put\r\n" + 
		"dup 102/f  put\r\n" + 
		"dup 103/g  put\r\n" + 
		"dup 104/h  put\r\n" + 
		"dup 105/i  put\r\n" + 
		"dup 106/j  put\r\n" + 
		"dup 107/k   put\r\n" + 
		"dup 108/l  put\r\n" + 
		"dup 109/m  put\r\n" + 
		"dup 110/n  put\r\n" + 
		"dup 111/o  put\r\n" + 
		"dup 112/p  put\r\n" + 
		"dup 113/q  put\r\n" + 
		"dup 114/r  put\r\n" + 
		"dup 115/s  put\r\n" + 
		"dup 116/t  put\r\n" + 
		"dup 117/u   put\r\n" + 
		"dup 118/v  put\r\n" + 
		"dup 119/w  put\r\n" + 
		"dup 120/x  put\r\n" + 
		"dup 121/y  put\r\n" + 
		"dup 122/z  put\r\n" + 
		"dup 123/.notdef put\r\n" + 
		"dup 124/.notdef put\r\n" + 
		"dup 125/.notdef put\r\n" + 
		"dup 126/.notdef put\r\n" + 
		"dup 127/.notdef put\r\n" + 
		"dup 128/.notdef put\r\n" + 
		"dup 129/.notdef put\r\n" + 
		"dup 130/.notdef put\r\n" + 
		"dup 131/.notdef put\r\n" + 
		"dup 132/.notdef put\r\n" + 
		"dup 133/.notdef put\r\n" + 
		"dup 134/.notdef put\r\n" + 
		"dup 135/.notdef put\r\n" + 
		"dup 136/.notdef put\r\n" + 
		"dup 137/.notdef put\r\n" + 
		"dup 138/.notdef put\r\n" + 
		"dup 139/.notdef put\r\n" + 
		"dup 140/OE  put\r\n" + 
		"dup 141/bullet4  put\r\n" + 
		"dup 142/.notdef put\r\n" + 
		"dup 143/.notdef put\r\n" + 
		"dup 144/.notdef put\r\n" + 
		"dup 145/.notdef put\r\n" + 
		"dup 146/.notdef put\r\n" + 
		"dup 147/.notdef put\r\n" + 
		"dup 148/.notdef put\r\n" + 
		"dup 149/.notdef put\r\n" + 
		"dup 150/.notdef put\r\n" + 
		"dup 151/.notdef put\r\n" + 
		"dup 152/.notdef put\r\n" + 
		"dup 153/.notdef put\r\n" + 
		"dup 154/.notdef put\r\n" + 
		"dup 155/.notdef put\r\n" + 
		"dup 156/.notdef put\r\n" + 
		"dup 157/.notdef put\r\n" + 
		"dup 158/.notdef put\r\n" + 
		"dup 159/.notdef put\r\n" + 
		"dup 160/.notdef put\r\n" + 
		"dup 161/.notdef put\r\n" + 
		"dup 162/.notdef put\r\n" + 
		"dup 163/.notdef put\r\n" + 
		"dup 164/.notdef put\r\n" + 
		"dup 165/.notdef put\r\n" + 
		"dup 166/.notdef put\r\n" + 
		"dup 167/.notdef put\r\n" + 
		"dup 168/.notdef put\r\n" + 
		"dup 169/.notdef put\r\n" + 
		"dup 170/.notdef put\r\n" + 
		"dup 171/.notdef put\r\n" + 
		"dup 172/.notdef put\r\n" + 
		"dup 173/.notdef put\r\n" + 
		"dup 174/registered  put\r\n" + 
		"dup 175/macron  put\r\n" + 
		"dup 176/.notdef put\r\n" + 
		"dup 177/plusminus   put\r\n" + 
		"dup 178/twosuperior  put\r\n" + 
		"dup 179/.notdef put\r\n" + 
		"dup 180/.notdef put\r\n" + 
		"dup 181/.notdef put\r\n" + 
		"dup 182/.notdef put\r\n" + 
		"dup 183/.notdef put\r\n" + 
		"dup 184/.notdef put\r\n" + 
		"dup 185/.notdef put\r\n" + 
		"dup 186/.notdef put\r\n" + 
		"dup 187/.notdef put\r\n" + 
		"dup 188/.notdef put\r\n" + 
		"dup 189/.notdef put\r\n" + 
		"dup 190/.notdef put\r\n" + 
		"dup 191/.notdef put\r\n" + 
		"dup 192/.notdef put\r\n" + 
		"dup 193/.notdef put\r\n" + 
		"dup 194/Acircumflex  put\r\n" + 
		"dup 195/.notdef put\r\n" + 
		"dup 196/.notdef put\r\n" + 
		"dup 197/.notdef put\r\n" + 
		"dup 198/.notdef put\r\n" + 
		"dup 199/.notdef put\r\n" + 
		"dup 200/.notdef put\r\n" + 
		"dup 201/.notdef put\r\n" + 
		"dup 202/.notdef put\r\n" + 
		"dup 203/.notdef put\r\n" + 
		"dup 204/.notdef put\r\n" + 
		"dup 205/.notdef put\r\n" + 
		"dup 206/.notdef put\r\n" + 
		"dup 207/.notdef put\r\n" + 
		"dup 208/.notdef put\r\n" + 
		"dup 209/.notdef put\r\n" + 
		"dup 210/.notdef put\r\n" + 
		"dup 211/.notdef put\r\n" + 
		"dup 212/.notdef put\r\n" + 
		"dup 213/.notdef put\r\n" + 
		"dup 214/.notdef put\r\n" + 
		"dup 215/.notdef put\r\n" + 
		"dup 216/.notdef put\r\n" + 
		"dup 217/.notdef put\r\n" + 
		"dup 218/.notdef put\r\n" + 
		"dup 219/.notdef put\r\n" + 
		"dup 220/.notdef put\r\n" + 
		"dup 221/.notdef put\r\n" + 
		"dup 222/.notdef put\r\n" + 
		"dup 223/.notdef put\r\n" + 
		"dup 224/.notdef put\r\n" + 
		"dup 225/.notdef put\r\n" + 
		"dup 226/.notdef put\r\n" + 
		"dup 227/.notdef put\r\n" + 
		"dup 228/.notdef put\r\n" + 
		"dup 229/.notdef put\r\n" + 
		"dup 230/.notdef put\r\n" + 
		"dup 231/.notdef put\r\n" + 
		"dup 232/.notdef put\r\n" + 
		"dup 233/.notdef put\r\n" + 
		"dup 234/.notdef put\r\n" + 
		"dup 235/.notdef put\r\n" + 
		"dup 236/.notdef put\r\n" + 
		"dup 237/.notdef put\r\n" + 
		"dup 238/.notdef put\r\n" + 
		"dup 239/.notdef put\r\n" + 
		"dup 240/.notdef put\r\n" + 
		"dup 241/.notdef put\r\n" + 
		"dup 242/.notdef put\r\n" + 
		"dup 243/.notdef put\r\n" + 
		"dup 244/.notdef put\r\n" + 
		"dup 245/.notdef put\r\n" + 
		"dup 246/.notdef put\r\n" + 
		"dup 247/.notdef put\r\n" + 
		"dup 248/.notdef put\r\n" + 
		"dup 249/.notdef put\r\n" + 
		"dup 250/.notdef put\r\n" + 
		"dup 251/.notdef put\r\n" + 
		"dup 252/.notdef put\r\n" + 
		"dup 253/.notdef put\r\n" + 
		"dup 254/.notdef put\r\n" + 
		"dup 255/.notdef put\r\n" + 
		" readonly def\r\n" + 
		"/PaintType 0 def\r\n" + 
		"/FontType 1 def\r\n" + 
		"/StrokeWidth 0 def\r\n" + 
		"/FontMatrix[0.001 0 0 0.001 0 0]readonly def\r\n" + 
		"/UniqueID 630181 def\r\n" + 
		"/FontBBox{-143 -241 1006 728}readonly def\r\n" + 
		"currentdict end\r\n" + 
		"currentfile";
	private static String testData2 = "dup/Private 19 dict dup begin\r\n" + 
		"/RD{string currentfile exch readstring pop}executeonly def\r\n" + 
		"/ND{noaccess def}executeonly def\r\n" + 
		"/NP{noaccess put}executeonly def\r\n" + 
		"/BlueValues[-15.000 0.000 464.000 475.000 687.000 717.000 720.000 722.000]def\r\n" + 
		"/BlueScale 0.033333335 def\r\n" + 
		"/MinFeature{16 16}ND\r\n" + 
		"/UniqueID 630181 def\r\n" + 
		"/StdHW[18]def\r\n" + 
		"/StdVW[82]def\r\n" + 
		"/StemSnapH[18 31]def\r\n" + 
		"/StemSnapV[82 158]def\r\n" + 
		"%./ForceBoldThreshold .5 def\r\n" + 
		"/ForceBold false def\r\n" + 
		"/password 5839 def\r\n" + 
		"/Subrs 16 array\r\n" + 
		"dup 0 15 RD ¿1p|=-“D\\âR NP\r\n" + 
		"dup 1 9 RD ¿1py¼öUz NP\r\n" + 
		"dup 2 9 RD ¿1py½Äži NP\r\n" + 
		"dup 3 5 RD ¿1pù NP\r\n" + 
		"dup 4 12 RD ¿1p~¶+6ä6z NP\r\n" + 
		"dup 5 5 RD ¿1pù NP\r\n" + 
		"dup 6 5 RD ¿1pù NP\r\n" + 
		"dup 7 5 RD ¿1pù NP\r\n" + 
		"dup 8 5 RD ¿1pù NP\r\n" + 
		"dup 9 5 RD ¿1pù NP\r\n" + 
		"dup 10 5 RD ¿1pù NP\r\n" + 
		"dup 11 8 RD ¿1py­ãì NP\r\n" + 
		"dup 12 9 RD ¿1p>~l* NP\r\n" + 
		"dup 13 9 RD ¿1pdK¦ª‰ NP\r\n" + 
		"dup 14 9 RD ¿1p\n" + 
		"Ëfà* NP\r\n" + 
		"dup 15 9 RD ¿1pdK¨7÷ NP\r\n" + 
		"ND\r\n" + 
		"2 index\r\n" + 
		"/CharStrings  78 dict dup begin\r\n" + 
		"/.notdef 9 RD ÖI	^\"âÏµ ND\r\n" + 
		"/ampersand 217 RD ¿1p^“6\\ÞÅ¡p*¶Dc²{D'Ë>Ã‰cy­uqÑ@ôY@@9â!ï\"\r\n" + 
		"kŽoÁùÈ©\r\n" + 
		"Dûè*ÔJèRìmtwIÚt/ä/óY^M§‘¡ñ<èÿP/ŒGý°/ÕÒ#¢4–ŽåÙÂrnVô 7EþŽŸ.\r\n" + 
		"Íwu€(HaÔ¸ÈÛ½Ÿrr9íÓöÀ·gœh…¶?üñ2¤^ÕéD;›·.³§£§WZUîÂp ¿pß\r\n" + 
		"oÃ›gHC0r¡\r\n" + 
		"Ó(´1¹‘@%Ê:\r\n" + 
		"á™‘õË6ýìxnßÆÔ¬^Ñ ND\r\n" + 
		"/quoteright 70 RD ¿1p;Åu÷h­ù&ÜÍþ´¤6°Óó?XÍ?Qð Ž¿ú(n|4;ÐÄ;…ÊÆ<.KÝžHË±H33áb0øj†¿Ëý ND\r\n" + 
		"/parenleft 64 RD ¿1pEµŽ)ÌI]—ÒÔ3ùo™Z€´t7øIÝ„gÀ«®Ìô¦ï>æö÷Ggå}¬/¾[†é\"®¤§…°F&™¬Q ND\r\n" + 
		"/parenright 63 RD ¿1phŒäËÕQ|Ürôšh.¹Êœò.¯†¡y5ÞªIn9ª˜íÌd²‚Å½FôÆŒüÀÝ”§jA?þò ND\r\n" + 
		"/comma 69 RD ¿1p;Åu÷h­ù1’(éÒ™¾*74e‡¶}¬¾1“£æZÚgã{ÙR=Æk·¬ÝÍ?7’äÞ‡8­û](=hŠëîÅ ND\r\n" + 
		"/hyphen 28 RD ¿1pD„;.î™1Ã×ñD8Édºò‹\r\n" + 
		"Š–8z ND\r\n" + 
		"/period 40 RD ¿1p)YF¦áèHÆ|½i’K@©ˆ·ïÕÈ²ž>i§Âtì¯ò¤¬Ó£§ ND\r\n" + 
		"/zero 86 RD ¿1pYšÊ¿–?w6 „~ìD*fôR×[ßô_þQ¦qÊ§ÍÄY4K¯ì«“%½§@?[\r\n" + 
		"ª–«œö1?\\„NÚ“’FLZM?Ôk\"¥æiÂ}-±ù ND\r\n" + 
		"/one 68 RD ¿1p –ÿj\r\n" + 
		"†øÍ	î[?ë`x¸Ýê?pÇØµ&ÂËÍ|éëwüïPmÅÀØÌœ|º«º;˜š*-L]z´ ?ºnŽ ND\r\n" + 
		"/two 92 RD ¿1pUÑº¾2³É¯)‹’eyé‰2ƒÈ¿Kô@e2UÁ—è¹{ˆ`ú¿þoêF¿]*çe®¢Ô ì¸i…\r\n" + 
		"Gf Tv†j\\–ºž·®èðmjÐÜ«Ÿ;N÷Nm ND\r\n" + 
		"/three 110 RD ¿1p6Ñ÷Ö=esÀÅþà¶bn)nA@)La±ËmÕz\\Ä‹ûkO¸˜O*e^ø”Â&V?F ?¨Ò%èÕ+}•˜w×ý»³þ?u}ZÝ¢ð1ÌÀ[®¡Ä—Ë¸F3ÒB,ÑC;¸`ò2Hï ND\r\n" + 
		"/four 67 RD ¿1pi±ýqI»][ôJ,Ö/kí}5†?%%èbJ,ÿJ£(9F¯Ck2¿<š•5ûñ Öšòaã0(µ¹¦ÿ? ND\r\n" + 
		"/five 90 RD ¿1p;ÊVòìÅr÷¨ †NŽ5m^¸Ñ”‹ä¹¶BíÝ×L‘ÉìÌÅ÷6d³…¦Có§Q¬y%÷P©ñÞ§AÖ…K?Ÿ¶ÓÈ?¼‡Ò-ò§kîüuÏ ND\r\n" + 
		"/six 120 RD ¿1pHUÖ´týÎŸ_š3á‰+°š)ø¾êÌ©yöƒÕ¦ú¢ÿ¬Ï‰î(4?u=hžOT,ð=vVèä¶qœ™zïSq9ïiøîæEbg.Ž™°öT•ý1è§ð–¼çš`’K¾²j iÐ=b×}L8? ND\r\n" + 
		"/seven 50 RD ¿1pG'$8Ç'\"kå1páFŸ&>9	õW¡=rÿ”YC—œÒ»†sgî)«;°S ND\r\n" + 
		"/eight 138 RD ¿1p7 ¯gCO9(ö;ß\"¿ìl.D—LäÐó>ø_šý‡&+à\"rƒý·ÁF€_DÔ?áô¯´‹{îA=¯ó[‘˜u®- V­¯S}Ÿ}ú?“:@D»;¯ Ý`óæc2£ÓƒïÅ[ÉÜ?g}Y-©Ç`ËF°JÆL9wnî¿G¯w2w•êU¤# ND\r\n" + 
		"/nine 119 RD ¿1pB/“Wcîr’–î¥=Y4÷$^\\Øá.¸ø¡HÃ§?OÅÇK6?8,TLc_§K(ÊÈŒ÷ß”’EmX‡÷çVu#u3ó°B3×Ð‹°K…Bä\r\n" + 
		"æˆ ¥àgŸÆ?j^ÅýwÝ?äÅÉÝ¶•h%På»“¼ ND\r\n" + 
		"/colon 70 RD ¿1p)YF¦áèHÆ9Ê±Ë'Ú“!®¬/»ìl`÷Vv ìê¿µ%[|ÀâÀ1L¦eöÀÝô?|gû½™ZAW¨¾àóP?œ| ND\r\n" + 
		"/semicolon 99 RD ¿1p;Åu÷h(K˜T©C‰€ZßåÎÁ×Ö«¢Jf¿R÷ 1‘Îhl18×›\r\n" + 
		"_îŠ?œ2nP¶ëÊ?YmD;ìZlsÚÒ¬å2E”~ñ:|GT)égJ?o¿¸æ¶¦%ù ND\r\n" + 
		"/A 116 RD ¿1pkxª7^têx]Oœ Ç1È,ÈjõðÎ7°`ƒ§Ð¦	b,ðyÐo-ræhDqŠŒ,Ä\"/½‘=îø(C¨èÜ1ºVÒìÔð?Hd¬î÷v¬Ÿì\r\n" + 
		"?û0Óg„YIby6©IÈw†È®´fë¯Ôe€èh ND\r\n" + 
		"/B 139 RD ¿1pFé¯ûS)€àÒ„ñÚAÂþl™ÝÃ>Uà%#ÚDF…¾êäÅä!]€1ij?jê¤EžÀ:9)ùÖ| }µN¶¯—ßìÜÔå£jÊ\\çwÕ‡\r\n" + 
		"F§5žG œî[»N‰}½?#,ìáú/š±8ßnLÿ“ÇŽUø›¤Q;ï¤bè€¹h ND\r\n" + 
		"/C 109 RD ¿1pG&½÷ÆdRNjíLª°=gŽ5´¥A2ç³á@K_\\|ÈÃ}¶tëÆnˆ .I„¸?3FÇ46ãîOgƒû{3í[?âJ^?Ü´ÕMå4‡tàz@÷åÇf÷K½sdÂ#ËqiÆ ND\r\n" + 
		"/D 102 RD ¿1pY›Ú>µÒX9è&\"ô€6OàóHšÉ€+ø’”UHÝ¦­7.tJ'­ÝÍ¾«äNré¾?èÑ,~´)AxƒõŠ·Ž¥§ÊÖ³­NQ„ÍÀ(Y#XJ÷o—¾%[>`ºƒŽ ND\r\n" + 
		"/E 121 RD ¿1p]eÀ?ôÞJöX÷6p?7½üt—;gš„¤[Ãxw½ÓÀŠVïa?yUÍ;=¥nS‰#›Ó:D@-k‡|WÑ£‚\r\n" + 
		")|—zœ!œdLD0=‚ØVõå(£ùÊ¹sŽ›¹oóÌSa÷Å§>wˆ~yEœ®M ND\r\n" + 
		"/F 110 RD ¿1pDŠž›Nµ.¤? üÆL6c—ù7 R gl^ÙZ‘ÛÕèòsÝæå:·šþ‚xª½¶ƒZZÆj©%‘†\"2¹ÊêÛ 5ÿµÅú?Ü?#?]µùs…ÞüýHB\\ôN¸V?pswÂ˜p ND\r\n" + 
		"/G 141 RD ¿1pDŠzƒl ?¼Çw^Ú]Õ=ì¶äf¸ÿä|	Ïå,WÀ¾Z¡ôË«'\\¢ÔÈ\r\n" + 
		"Æ7€H½¸fìŠÎê%.Pl®wg)ÖÛÏÇ?\r\n" + 
		"¢»#	¶Ò‹ŸïAïò˜ÿÅ]íÃ¶QAµ¤ž)iáÚ¡³m¨ê É‰åîà\\U—a‘%ög¡j‹¦0cæyì[ ND\r\n" + 
		"/H 131 RD ¿1p\\7ö!zoZáT•?f* Û\\f6ƒ]%¡	eG‚_¶¢©®+ôkð§é\"äÄÍ’aY'å¶RÈ¥ÇÏ?©}g²V»1Jà™—è?o ¤öDHr,zJ‰Çó\"ªêˆßªu¬€Tãïš²?J¤	f ¨õ¸þÇl)˜¡	ò‡øo ND\r\n" + 
		"/I 63 RD ¿1p\\6z˜ròÕ'È;:ý0ÛÖtÏ|#/Qy_b¥[äQÂ—#G§‚§“ö¨KÔ‡ L®Ý&ÇŽ¥î?´ ND\r\n" + 
		"/J 78 RD ¿1pStJ.§’ðÿíâîgæg(ê¤QLšÉ±³?—–iæ*ä[ò;¥ôö®ŸØt?÷G±€®‹`êJPë×9÷DÿÑq;ð©?\"iÍ…«vn ND\r\n" + 
		"/K 146 RD ¿1pAñ;!‡Âyûß_\\Â\r\n" + 
		"t‹xØeê2¥T&¿tX´šZ(ñpUá°äœÞÚ¾`ŸòoOÙ}Ë£sÆ*þ?ö?Ä¿‚.\\Rðäj‘\"Z¼?ú<¬âÁ¬7›IÇAZ¦¯ž\"ÞIæ¡Èd	FjêÀ´¯«—Ä!?¢¡`Õ¶<‰ú·HÒèO?»•z”ßMeü ND\r\n" + 
		"/L 82 RD ¿1pG&ñåÕÒ?†qsd­@FãÅ™Ï2ÎZWs›+ìÉ“5ô÷?£ãÐF-öèaúxÚØôŽÇŠcàaí9§çf?ö\r\n" + 
		")Ø‚CHYŸ™Ç¦F44 ND\r\n" + 
		"/M 128 RD ¿1pD‰?¼k…Õã6öã’sïaÂs:nRråÚK2QóñC­¸Zâw‚EobjeKþ•/²É\"‚m@^?÷pÿMAå	Œ±qFgVÜöÌZYÂéÝ	·*æöë0î³š\r\n" + 
		"û™$ÁÉÿé„¾;»îÑ\"[‹ç¾_¬…” ND\r\n" + 
		"/N 103 RD ¿1pjáÎõ?Vn_;÷/ûGÆ1sY$á÷ Æ)ahÐÊÍâsý>þI3>%”©ÊS‡.SÝh¿ëuG¡ƒuª›9ÖO.¾•£Þ¨¸ä¤1»®l8:7?‹'a/uð	E†$z¸w5 ND\r\n" + 
		"/O 101 RD ¿1pB.Ú\r\n" + 
		"\r\n" + 
		"ÅG8T««­˜ýš6ä„?<Ðj:«ó¯À+œ…µª)W™*qä ït~†KE|UÍÃ¼PÇ»2ä'¼ñÄ»¨7`Ä‹ÄDÕXbB×“OÝÛÍý@‘ùå?¹~ ND\r\n" + 
		"/P 116 RD ¿1pV®œ&«Þ³×ÿS?Öw?íun‘c\\÷/ŸÛýÛìî´¼+«sŸb“è?VPyb!Ñ_ dpY>YÌOí\r\n" + 
		"Ñ*{çù «É bï’ie(W~L¶º£{!¾\r\n" + 
		"ù%ÍÏ<ï¿Àž×’}gÌw(˜Vpª ND\r\n" + 
		"/R 128 RD ¿1p_À¦¿XÿÿN¦9(4üKb†-v‡†íÞ£fÉZ;÷°F\"Þ!ºÆû˜'G­qöíÚóq…ÈC­^?½uÚÄLÞÌòæ6#ZJ¾œÊ¸FU^\\µÑ»˜Š²ú¸çûàŒ\"ÑìõBšÒ/ÿbóí+Bü?tn¤ˆÔæ›êÌ ND\r\n" + 
		"/S 150 RD ¿1p7 /Î\\VÑù»G3#?¾œñD9í¾²wrÛ *Bì,dro®‘ÝîÏ\r\n" + 
		"”[ÃŒ·µµÕ©zr\r\n" + 
		"Ì¢BÁsñ£Ì•¸?ØÐÇ$˜9gß—9Qö{}ƒ.7&ð]s”ƒDÛ­°x*IŸœ¼Œý(A(²Dðf´àÏGó*ùä*êX•;Ÿ$>ÊÆ`qÜÏ ND\r\n" + 
		"/T 83 RD ¿1pV¿¡+®ª‹?-µÁæ‰þ–œrÕÆÜ©xœ­üýXÙzOê ¾—¬—F.å¤DËt&ñé?öiHÏî÷:âÃáAUJUN'Îç\r\n" + 
		"%ï… ND\r\n" + 
		"/U 105 RD ¿1pY›Ú>¿—c °/í‹i³ŒÇÃ:kýç²?Gsú•ÚÛUŸÒPWkthU?D>Ý‡\r\n" + 
		"”¡šú¬Œø.wè+Ö¼úE}ÔøòïÙ¬ŸDm~Ð÷›4+ŠüÓï±À¡nO±Yï`ŽTë ND\r\n" + 
		"/V 99 RD ¿1peöÍ~`”I,òÑtRH(?êð’OéUìñ:\\¨Úpìø aÙXdÖ´Îîlþ½¹’O (Ä’?³zwbÅÎ©³Uû?mëÓº˜Ÿ8‹.ŽNhR; ND\r\n" + 
		"/W 156 RD ¿1p`ý=R†¹»,3H%·‘G–Ã=m*ï‹—xÁ]ÉäQ÷‘•£ Ñ“9%;òp	%™Â¥ænH. v‚ÀÔBïß-¬Èä9¹?+—´5lö/óÝ‘…ÚÊºb¬¤œÐ©Âh\\˜&#ÎÄâ¹\\f?ž…NÄ7$˜ê’N0$âúæÃìÏ>é÷³ ›q¨‚®–éQ=€ŽbçCsX ND\r\n" + 
		"/X 171 RD ¿1pjáS•›.ìF>ÝTZ7£É¤Ôöön¼‹ûíîÎ-?½Üutým½dmù7ãµþêŠp\r\n" + 
		"Å“ÿ‡ÕL.×MrÚäM«kDt™˜P„ðÀ×?ÅûSëyùÆ/ Ô‹–{EŠþüÊñM-&%¶|ˆí•7ï?|]?Ûü%Ú€ÉËˆ	º%:j3«%`vw²—¿8Ü¿~i%_ÎÈVðã¯Ã4Í ND\r\n" + 
		"/Z 71 RD ¿1pRDž¢(ºÚðäôè[®-º¤ï(Æj¾4û¸«sÇ#qcËwÿÀÂýŠ$q„Côd™‚˜Ä(.=©9ÞºPdý³ ND\r\n" + 
		"/grave 67 RD ¿1p;Åu÷.ß]Ÿÿ”(T@PhûkÍ¼©º?ˆ >?ÈvÑðF/jøµÌ5Y\\‚6æ¸„¾U*Hªâº3¢ ND\r\n" + 
		"/a 155 RD ¿1pAð©IE²ç¹k\r\n" + 
		",·þ{,@Ûlí,–#³¸Pº±÷)©v‡8rÛà> Ÿ?ÛÍ‚Â¢ÜYCîTMØú4–¸+‚ßº¤Òèþ«H¶èàSæXy„?ÐÞjŽBB„;â]Ïøå‘cÜ_ªbaì\r\n" + 
		"íïz»?0á?$…J¤B?&h¤jæ–Ó	‘/²[|§ud ¶Ó­T¤žÑbŽZ ND\r\n" + 
		"/b 128 RD ¿1pREÃìO¾@\r\n" + 
		"ÒMV¯.ÃP—à$â¿sæ\"­Ð?ÙÐÑzÔØ1¯³a,ê”hÚ8WÍ° |=$û”:×i&Ù>ægêZáZFK)8j§Fn¯Áý l5þë[ðçìƒÆ­ô§ÓŒüø?»«o4Í]¬ùd‰Ù¸š°YìžA|ª ND\r\n" + 
		"/c 96 RD ¿1pQ‡ÏiÜ[`q?Ýw‘H4J˜(CñøSë&šW–jbó?Ù½Ò–Wûñ„ì]>E„6´¹îáâû@Žµ‹³çËaJÎ¦t¬u¨.K)œ¼üœ?‰ ND\r\n" + 
		"/d 155 RD ¿1pYšþ­Ãrcv?ÿÚ^d€Û%ºÊâè=X ÜNÉE9/9ÏÁàf¤® <R—4Ü?ÖÓZp‚1è?ÊŠ”à¦Ê‘Áóÿ’U|§?1¢Ýîœ&ÌpÓW9½Ó¬ LK[?¨ë¹gËP!k½Øã¼˜|?,,Uê~UeÙ£Æ^Ùcû¦ÓªCt«à#äìÕ+Ø¶‡’¤Ü$–ÙÊ”H|(ø8 ND\r\n" + 
		"/e 99 RD ¿1pUÑxÀ-l?ÜºQ'ô…Oôlô˜”—@C1®H$Å{$‡‡%š€ÂRA@Z¡³:T?œŠ?Ü”¨óÀŸ¶1ž­q1½À	?ã@ÈSž”~.OVñµ‰ý”±í3/®¨ƒ(kß½ ND\r\n" + 
		"/f 93 RD ¿1pQ}AA?“n!ûZ5G²dTÙX\r\n" + 
		" #EEÞš ¹­€GF6…~Ù%®ñó?—ýÓØYÙ/½P÷EÐì%à\r\n" + 
		"Ö'bÆa›Ý /AÝæ\\ã»¯&Æ‹ž’Øø,JVÆE ND\r\n" + 
		"/g 211 RD ¿1pYšÊ¿–Ìuò«kl¹XD9?3NN¬XlýJâ#ösm/êÉ1Ý­žPa%*Èc%ú–Ë¼?&^¹_¢åÃ°\r\n" + 
		"\r\n" + 
		"\\³W¶Ñûn,ÉCïu¹næÛsj?•$,Áˆo\"-4EÈ./\r\n" + 
		"pà×Ÿ{\r\n" + 
		"Ÿf÷ˆ“¹gû_` M„Y‡ÒËvðbê €aâ¡ŒbýûÐÀ47\\¡a9\"SÒ2%ù~>\r\n" + 
		"m\"ç8ì|¿÷2Q¹sØs&sRd×C?›¨Q`-³°Ÿ-2°°€y ND\r\n" + 
		"/h 134 RD ¿1plLoËýl..	mîÝÂ6Q?e:(ð§J$üZRC™”ñ^X´‚bV}ÃdBýJX ¥?ÑvF©$ßS¤\r\n" + 
		"ËBöæ–(Š3Ã.îN?û*×y«H3äÔÔ>~·[ÏÌ?qß]Ù‘ß¤xÙ¼Ùs½G«FŸ;«Ý&¶[ZÜ\\ßQ[è?‘‰¦ÕZ ND\r\n" + 
		"/i 103 RD ¿1pPÖ•V~Qm«ÔÌ?¢Ý¶gVÑÂgð_Òâ&‰S:ÒU?L?àIŠ£à¨9škºººÂ×>	«K9Ù¼=Ê_.r?œuj¶1Îz áÆÛ³ãÃbÅÖy¸:£ærG]P¯YÅ ND\r\n" + 
		"/j 115 RD ¿1p°~‹®³aö®÷ÛqJ´¸Ö:OsÐÛM½²K-]Éá'¢ÉÌ­ñ£\"ï	“c¼UßI’{5‘W-äÀÉ(ªycì¨\r\n" + 
		"j,ì5ov°šÆAÌ:+äìølüí5í™UOÛ‡ˆW/\\f+Í{?¬™Oß% ND\r\n" + 
		"/k  153 RD ¿1pW>bÓÒ¾ O&Öê•ÿ „qXv–„«T¥÷o³Â¸—h0ØõìÉNIXÄ¥âºÎæîŽ¨,„àGaÉ?1$&ZÂ+7$–¿ ˜U?7Lu2XHw™Ê“ˆ†°…À¦Q’\r\n" + 
		"ú}åJ¶³?YûÖ”1¢ù•«QÂ‚ üIeæåPüyïm³hÄ‰[‚ØeÛ\"þ ND\r\n" + 
		"/l 74 RD ¿1pPÖ•W¯s\\³´7+yLúˆý†ÜOëýi\\äjBO@¡‡eŒ?2üm£w7\r\n" + 
		"[IÚ¸h&yE1Çÿar÷Ê*úSWÇ1Â0S ND\r\n" + 
		"/m 188 RD ¿1pa/9Ç.Ûˆo¡ý’2Ûý V¢¯¸‡Ù‘âŽ³H—ì$9|ÂX\"È?¹é'rªÐv\"\r\n" + 
		"Uw# öu×ýÂrv@	JÑôü×±ƒ^9pŽ°‹‹LëµŸi /²1·ŽgÎP*@¬\")Pÿþºdèƒ´¹¾ÏË¿¡#‰“<º‡æl»ññ-ðÓn2ímÈÛ aSá‘|XpÃƒ¦ó:¯[701ƒêC=-žãYw³D`?5/ýäŽ.ÆßmÄ¢ËÞŽ ND\r\n" + 
		"/n 135 RD ¿1poØ:<g:iÛvxt¦+MÏM´Àåñ¤CãÛ€t¼êZ!‡Z%L±ÜJàuXuµH‚ÊjeÈô‡gEÑ\r\n" + 
		"ô+Œ€?¬B÷„>™A©t;ÒÐ4˜êÚê‚´4hÒÍ^‡d¯;ÏÆÞ¤$é.=ù2€+·šùw“øëS1B¦Yé*[¾/» ND\r\n" + 
		"/o 90 RD ¿1pJ²,;{Í®*]P:ÈfÜs¦Å”B4ãõ,7§OËèômï©]G|ËÊÌ)ÚòëOo8á¶:s®è›qÚM?F‘cwì¸žÎ?Fu€eŒˆó ND\r\n" + 
		"/p 145 RD ¿1pn©¹	¸?Q7vX–0úADØn)÷F°4¢È«æÔ¼¿M~d›ÖŒÃ? qÎOUeô†õÐ;ÕGGÜ–šÕËºçY{çØB¼ÕæD”<OtËMLËñmì‰²|Ú /ê‹zQJ)x…ø?	%,Ü WÌçé—ŽÉPŠ—ÙçuhÇ‹ñjÖè¢?áï4ˆÃêó ND\r\n" + 
		"/q 125 RD ¿1pB/§v]•?(¨!Õ\r\n" + 
		"­]©W®ØÈZé\"Ð]¾ë#DÊÇáEh“¢^3–±°éEvùlÆ,ÖVß®1®8Ä$§_ñÓË(z»¢µQWgw%ÉÝÅ?–ÍVr=U‚0š‰fb’*×íË)?˜, #YM©%õ©nŽ?ØOC2 ND\r\n" + 
		"/r 111 RD ¿1pT¢§Hø^,ø¼L¾%f]ý÷ö’€Ú5ƒGžþÿA´íoNåÌE4KxšK{Ó¬fúø´NËç1B#'ïc»’PÃÐÆ6a‰ñ&N°1ý»ÁÛ÷]?|·$6µL¿´§3M'ˆ¾E p¤4 ND\r\n" + 
		"/s 145 RD ¿1pN|zqBUu—sX²gwÑvbD¨Y=á4Äºþ ·Ág3ÓÞr&ÐZ›ò¨ð®’¨úD¢9SU\r\n" + 
		"lÅõwnòx¨H)9RŸÞ/ÚÅÔiÅˆÃ¾Gœ±y¢”³1cª$šé……\r\n" + 
		"h)¯3ö­?Z·|K<]–KÔ<ÁÉèš16?6ø3Ü¶á?ù¬«¤È˜ ND\r\n" + 
		"/t 76 RD ¿1pZÆ¶U|—÷Êˆ·/€[ÝËÄÿÚS±Æ?tó°t(ÿZwAÑTµŒUßxýüŽž;IäÑÀ<ðÈ–òD$·€r´£é? ND\r\n" + 
		"/u  119 RD ¿1poØ:<Oºs›w&˜îÉòtè\r\n" + 
		"~3Áýk¦a¥ŸðL¬|£ëLrÌÑ$Ûäú…ÔL& ZÍJÚ=j…Ä?»¢oçýŽ1!¡ˆ¼Io#p€s{Uƒr%\"ð?Ö­m„ó–`?ïÒ:åª4N½è¾?Ð<Y}ƒ¾O³‘Ñì ND\r\n" + 
		"/v 97 RD ¿1pjà}ÑrJ6É²·xœÀ©ÎÎ  ?	É¥¦Ä\"Æ'½¾m«4F¹?@j¢Y‘ñ1'0to”½Âhç?ú)d± ö4¸?Ã³ï–-Ø?«bè»)„'Š‡}y„ ND\r\n" + 
		"/w 150 RD ¿1peöÍ~aúw:ÜrT?Ç =ø”\r\n" + 
		"cj¾›¬èw†–²W[IZ8S¥Î\r\n" + 
		"Ï½oS”DaÆ_l9ÛÜC#ÚSl¬\r\n" + 
		"?ÄÇQ_«`&l§eà³jskFÿÕ)¶˜³ƒ«cyˆ3ÓØ¬/D	Œ%të¯‰êïº^XIÃ]swæ6)b¾$8V%Ñy¦ôæHÙÉÙD;zí ND\r\n" + 
		"/x 160 RD ¿1pn©?„š†° o»§±‚”±c|È3Ö¨ÿxÁú¤Ð~ ÷/I?m+]EêÅ²íÞ,×¼ŽMAƒ_Œuv–£ˆÄéà¡Ù^ƒ~? *#\r\n" + 
		"Ìè¡ÒQÏˆŠ;†·”Ž¯\"+™¢\r\n" + 
		"ë÷º•}ónZ¥(§Ñ”i¡{@¼8êÏ™wS¦{ßÕ‚kgýŸ›ÄÍn²4Ä	Â€Äµ¼¸ ND\r\n" + 
		"/y 129 RD ¿1pi±ýqIÐ¢¿=œ ¿+JQÝ#ò\\ÄÿÆc¥Ê·©u	?íß¥?›~i' ¨,NîuÊ<µ~dê6ñeÞ?‹d@ˆÅ*wP×BÀRN·µGq¿SŒËQÎ¥\"Õ¢¦³+\\TL5‡’'ÊHÊ¨Õöø-Ø2TÀ/¼ÝÏw½Pâ-™*;– ND\r\n" + 
		"/z 71 RD ¿1pkkœˆQ™Hé½–å20-¹\r\n" + 
		"¶k¿£ÉÍ—ßv–?ƒÒœ¨Ô%¡d^ˆ›#êD6Ä>æÿ‚?z\"ç\r\n" + 
		"÷H8Ýìþ£>ÁÞ ND\r\n" + 
		"/OE 177 RD ¿1p± ÝŒÑO¦ÍTìFùé(•øwæˆQ¥6ˆ˜cÅÜqÄžà¨•}Upß¬ãô?ÿ“é‚þp·†Âtù¨4YdbÎ®±ŸÀùY˜·$Úœ8tÝÐz?UkD4‰{%ð®$¶Ñ°lÂóµ¤“´…Ó¬­rp¯ò¶ÆºMn«‹y¨[lNC†=ª°à??ÕÌIÈùRÛºœÑ\\ßäæ.o¿_ ¼8ºy¾ ND\r\n" + 
		"/bullet4 213 RD ¿1p?¨!??1²>Üì×ž¶˜+=J0Ö[$Â2exAÌn†·I[—ÉÆã8W;ò^B[Ÿ#W|»mzÇyŒ¨§3á?q!·cÙ‰:ç?r¬¦˜âÜ?BG©ô > Óþ†wÁ2×ñÙž8\r\n" + 
		"ëZÀF·¬áÎ\r\n" + 
		"\r\n" + 
		"Ø>©” Y?\"Jk2°Ò6_2 úêx¤LŸ ßSÅˆb)\"¶b?T†Ãn¶Íh(J­rkˆhO×häT'HŸM‰»q‰µÂ?›Á¾çl›ÞÿAÞ‚ – ND\r\n" + 
		"/registered 138 RD ¿1poØ:<g?ake%©KX—º»$mÊ²ïSçÅaü`šx2=G|ø.™+LOýN£ïÌ›ÄD§Ã©h«Åàÿ7D‰Ø.áÚ£ß\r\n" + 
		"ïâÏö¨==Lµ¯HœHž›nƒ/»Ð%9w¬qhì	Xõ§íŠ´½È`X5—¡Õ›|Vg™éÙÇ¢Ûò ND\r\n" + 
		"/macron 136 RD ¿1plLoËü?Jû	<ƒìŒr”œ_”bé_7ö.-È\"g¯Ø—›”£7äÞç¯­Î§ÀWfãŸSn>÷[¢’¾%Ì‡²”™¹£IÉÔðŠx?$5åµ| êötª $PqÁúÛ“VÜd5œ?—8d*¾¡ë?å›‹œxn¼ºò\"€/Ò ND\r\n" + 
		"/plusminus  24 RD ¿1pwký˜Šº°ìð\\¸?2/@ä?m†ê ND\r\n" + 
		"/twosuperior 133 RD ¿1pMMg™+Âã•¿âÉ7žU	/F.¸ÇPÑæ¼¬!?=6‰Vqw´¶è\"Õ_xü±·îƒª¹¤ÌxmäTijçô'Gaä?\\Ò«£J„Ë›)g‘dOñ,•÷TçqÂÔ±èÈ‘ÅÎ‘0>vÜž1ÖÇ¹|E'Ã—·Dæ	ü‘ ND\r\n" + 
		"/Acircumflex 27 RD ¿1p«vP ˆÈ²ŸÕ‘™œ‹ûöÒÞÎôð®» ND\r\n" + 
		"end end readonly put put\r\n" + 
		"dup/FontName get exch definefont pop\r\n" + 
		"mark currentfile closefile";
}