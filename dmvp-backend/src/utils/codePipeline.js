'use strict';

/**
 * TDD v5 Phase 2 Step 2.5: Code Pipeline (L2 + L4)
 * Technology: Node.js + Tree-sitter v0.22+ (100+ language support)
 * 
 * L2: Source code parse → AST → strip variable names/comments → structural hash
 * L4: CodeXEmbed model for semantic embedding (Python microservice)
 * 
 * Why Tree-sitter: Variable rename → same hash → plagiarism detect
 */

const Parser = require('tree-sitter');
const javascript = require('tree-sitter-javascript');
const python = require('tree-sitter-python');
const typescript = require('tree-sitter-typescript');
const java = require('tree-sitter-java');
const cpp = require('tree-sitter-cpp');

/**
 * Language to Tree-sitter parser mapping
 */
const LANGUAGE_PARSERS = {
  'javascript': javascript,
  'typescript': typescript.typescript,
  'tsx': typescript.tsx,
  'python': python,
  'java': java,
  'cpp': cpp,
  'c': cpp, // C uses C++ parser
};

/**
 * Compute structural hash of source code (L2)
 * Strips variable names and comments, hashes AST structure
 * 
 * @param {string} code - Source code
 * @param {string} language - Programming language
 * @returns {Object} { structuralHash, astStructure, language }
 */
function computeStructuralHash(code, language) {
  const parser = new Parser();
  const langParser = LANGUAGE_PARSERS[language.toLowerCase()];
  
  if (!langParser) {
    throw new Error(`Unsupported language: ${language}`);
  }
  
  parser.setLanguage(langParser);
  const tree = parser.parse(code);
  
  // Extract AST structure (strip variable names, keep structure)
  const structure = extractASTStructure(tree.rootNode);
  
  // Hash the structure
  const crypto = require('crypto');
  const structuralHash = crypto
    .createHash('sha256')
    .update(JSON.stringify(structure))
    .digest('hex');
  
  return {
    structuralHash,
    astStructure: structure,
    language,
    nodeCount: structure.nodeCount,
    functionCount: structure.functionCount,
  };
}

/**
 * Extract AST structure, stripping variable names and comments
 * Keeps only structural information
 */
function extractASTStructure(node) {
  const structure = {
    type: node.type,
    named: node.isNamed,
    children: [],
    nodeCount: 1,
    functionCount: 0,
  };
  
  // Count functions
  if (isFunctionNode(node)) {
    structure.functionCount = 1;
  }
  
  // Recursively process children
  for (const child of node.children) {
    // Skip comments
    if (child.type === 'comment' || child.type.includes('comment')) {
      continue;
    }
    
    const childStructure = extractASTStructure(child);
    structure.children.push({
      type: childStructure.type,
      named: childStructure.named,
    });
    
    structure.nodeCount += childStructure.nodeCount;
    structure.functionCount += childStructure.functionCount;
  }
  
  return structure;
}

/**
 * Check if node is a function definition
 */
function isFunctionNode(node) {
  const functionTypes = [
    'function_declaration',
    'function_definition',
    'method_declaration',
    'method_definition',
    'arrow_function',
    'function',
    'def', // Python
  ];
  
  return functionTypes.some(type => node.type.includes(type));
}

/**
 * Compare two code snippets structurally
 * Returns similarity score (0-1)
 * 
 * @param {string} code1 - First code snippet
 * @param {string} code2 - Second code snippet
 * @param {string} language - Programming language
 * @returns {Object} { similar, similarityScore, structuralHash1, structuralHash2 }
 */
function compareCodeStructure(code1, code2, language) {
  const hash1 = computeStructuralHash(code1, language);
  const hash2 = computeStructuralHash(code2, language);
  
  // If structural hashes match, code is structurally identical
  const similar = hash1.structuralHash === hash2.structuralHash;
  
  // Calculate similarity score based on AST structure
  const similarityScore = calculateASTSimilarity(
    hash1.astStructure,
    hash2.astStructure
  );
  
  return {
    similar,
    similarityScore,
    structuralHash1: hash1.structuralHash,
    structuralHash2: hash2.structuralHash,
    nodeCount1: hash1.nodeCount,
    nodeCount2: hash2.nodeCount,
    functionCount1: hash1.functionCount,
    functionCount2: hash2.functionCount,
  };
}

/**
 * Calculate AST similarity score (0-1)
 */
function calculateASTSimilarity(ast1, ast2) {
  // Simple similarity based on structure
  const typeMatch = ast1.type === ast2.type;
  const childCountMatch = Math.abs(ast1.children.length - ast2.children.length) < 5;
  const nodeCountMatch = Math.abs(ast1.nodeCount - ast2.nodeCount) / Math.max(ast1.nodeCount, ast2.nodeCount);
  
  let score = 0;
  if (typeMatch) score += 0.3;
  if (childCountMatch) score += 0.3;
  score += (1 - nodeCountMatch) * 0.4;
  
  return Math.min(1, Math.max(0, score));
}

/**
 * Get supported languages
 */
function getSupportedLanguages() {
  return Object.keys(LANGUAGE_PARSERS);
}

module.exports = {
  computeStructuralHash,
  compareCodeStructure,
  getSupportedLanguages,
};
