import web
import pexpect
import re
import json

# Point these two vars to your word2vec distance binary and trained binary model
word2vec_distance_path = './distance'
word2vec_binary_model = 'vec.bin'

# Spawn a subprocess and interact with it.
command = '%s %s' % (word2vec_distance_path, word2vec_binary_model)
child = pexpect.spawn(command)
child.expect('Enter.*')

# Used for getting phrase/value pairs from the distance output
pair_pat = re.compile(r'^\s*([^\s]+)\s+([^\s]+)\s*$')

# Random ass functions for interacting with distance
def rm_underscores(x):
  return x.replace('_', ' ').strip()
def rm_spaces(x):
  return x.strip().replace(' ', '_')
def parse_line(line):
  result = pair_pat.findall(line)
  if len(result) == 1:
    (phrase, sim) = result[0]
    words = rm_underscores(phrase).split(" ")
    qwords = [{'type': 'QWord', 'value': w} for w in words]
    result = {'qwords': qwords, 'similarity': float(sim)}
    return result
  else:
    return None
def parse_output(output):
  results = []
  for line in output.split('\n'):
    result = parse_line(line)
    if result:
      results.append(result)
  return results
def distance(query):
  q = rm_spaces(query)
  child.sendline('%s\n' % q)
  child.expect('--*')
  child.expect('Enter.*')
  result = parse_output(child.before)
  child.expect('Enter.*')
  return result


urls = ('/similarPhrases', 'similar_phrases')
app = web.application(urls, globals())
class similar_phrases:

  def POST(self):
    data = json.loads(web.data())
    phrase = data['phrase']
    result = distance(phrase.strip().lower())
    web.header('Content-Type', 'application/json')
    web.header('Access-Control-Allow-Origin', '*')
    web.header('Access-Control-Allow-Credentials', 'true')
    return json.dumps(result)

if __name__ == '__main__':
  app.run()
